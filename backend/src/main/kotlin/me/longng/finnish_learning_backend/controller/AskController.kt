package me.longng.finnish_learning_backend.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import me.longng.finnish_learning_backend.config.AskExecutorConfig
import me.longng.finnish_learning_backend.config.AskProperties
import me.longng.finnish_learning_backend.controller.dto.ask.AskAnswerEvent
import me.longng.finnish_learning_backend.controller.dto.ask.AskChatRequest
import me.longng.finnish_learning_backend.controller.dto.ask.AskErrorCode
import me.longng.finnish_learning_backend.controller.dto.ask.AskErrorEvent
import me.longng.finnish_learning_backend.controller.dto.ask.AskFitCheckRequest
import me.longng.finnish_learning_backend.controller.dto.ask.AskFitReportEvent
import me.longng.finnish_learning_backend.controller.dto.ask.AskSessionResponse
import me.longng.finnish_learning_backend.controller.dto.ask.AskSseEventNames
import me.longng.finnish_learning_backend.controller.dto.ask.AskStatsResponse
import me.longng.finnish_learning_backend.controller.dto.ask.AskStepEvent
import me.longng.finnish_learning_backend.controller.dto.ask.CreateAskSessionRequest
import me.longng.finnish_learning_backend.domain.ask.AskMode
import me.longng.finnish_learning_backend.domain.ask.AskSession
import me.longng.finnish_learning_backend.security.ClientIpResolver
import me.longng.finnish_learning_backend.service.ask.AskSessionService
import me.longng.finnish_learning_backend.service.ask.AskStatsService
import me.longng.finnish_learning_backend.service.ask.ChatService
import me.longng.finnish_learning_backend.service.ask.FitCheckService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.core.task.TaskRejectedException
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Duration
import java.time.Instant

/**
 * The anonymous API of the recruiter chatbot ("Ask Himmel").
 *
 * Anonymous on purpose: recruiters don't have accounts. It is protected by the Turnstile bot check
 * that hands out sessions, per-IP limits counted in the database, nginx's `limit_req`, and the
 * global spending cap.
 *
 * How `POST /chat` answers: a chat answer takes 2–8 seconds, so it is streamed as Server-Sent
 * Events (SSE): the response stays open, and the backend writes small events into it while it
 * works (see [AskSseEventNames] for the events):
 * 1. On the request thread: find the visitor's IP, check the session, and run
 *    [ChatService.checkCanAsk]. Their refusals are still normal HTTP errors (400, 401, 429),
 *    because nothing has been streamed yet.
 * 2. Create the stream and hand the work to the `askExecutor` thread pool. The request thread
 *    returns the stream to Spring MVC at once and is free for other requests.
 * 3. On the worker thread: [ChatService.answer] reports each step, which is sent as an event.
 *    Then the answer (or an error) and `done` end the stream.
 *
 * Errors after step 2 can't change the HTTP status any more (200 was already sent), so they
 * become an `error` event. Messages and answers are never logged.
 *
 * `POST /fit-check` works the same way with [FitCheckService], ends with a `report` event instead
 * of `answer`, and gets a longer stream timeout plus `:keepalive` comments, because a fit check
 * can be silent for 20 s or more while the report is written.
 */
@RestController
@RequestMapping("/api/ask")
@Tag(name = "Ask (recruiter chatbot)", description = "Anonymous chat about Long's professional background")
class AskController(
    private val sessionService: AskSessionService,
    private val chatService: ChatService,
    private val fitCheckService: FitCheckService,
    private val statsService: AskStatsService,
    private val clientIpResolver: ClientIpResolver,
    private val properties: AskProperties,
    @Qualifier(AskExecutorConfig.ASK_EXECUTOR) private val askExecutor: TaskExecutor,
    private val keepaliveScheduler: AskKeepaliveScheduler,
) {
    private val logger = LoggerFactory.getLogger(AskController::class.java)

    /**
     * Starts a conversation: checks the Turnstile token and returns a new session.
     */
    @PostMapping("/session")
    @Operation(
        summary = "Start a chat session",
        description = "Checks the Cloudflare Turnstile token and returns a session id for the chat.",
    )
    @ApiResponse(responseCode = "200", description = "Session created")
    @ApiResponse(responseCode = "400", description = "Blank or too long token")
    @ApiResponse(responseCode = "403", description = "TURNSTILE_FAILED: the bot check failed")
    @ApiResponse(responseCode = "429", description = "RATE_LIMITED: too many new sessions from this IP")
    @ApiResponse(responseCode = "502", description = "UPSTREAM: the token couldn't be checked")
    fun createSession(
        @Valid @RequestBody request: CreateAskSessionRequest,
        httpRequest: HttpServletRequest,
    ): AskSessionResponse {
        val session = sessionService.create(request.turnstileToken, clientIpResolver.resolve(httpRequest))
        return AskSessionResponse.from(session, properties.session.maxMessages)
    }

    /**
     * Answers one chat message as an SSE stream of progress steps, then the answer.
     */
    @PostMapping("/chat")
    @Operation(
        summary = "Ask a question (SSE stream)",
        description = "Responds with text/event-stream. Events: `step` {kind: GUARDRAIL | TOOL_CALL | " +
                "COMPOSING, tool?, args?}, then `answer` {turnId, text, toolTrace} or `error` " +
                "{code: BLOCKED | CAPPED | CONVERSATION_LIMIT | RATE_LIMITED | UPSTREAM | INTERNAL, " +
                "message, capScope?}, then `done` {}.",
    )
    @ApiResponse(responseCode = "200", description = "The stream; failures after this point are `error` events")
    @ApiResponse(responseCode = "400", description = "Blank or too long message, or a malformed body")
    @ApiResponse(responseCode = "401", description = "SESSION_EXPIRED: unknown, expired or from another network")
    @ApiResponse(responseCode = "429", description = "RATE_LIMITED or CONVERSATION_LIMIT")
    @ApiResponse(responseCode = "503", description = "BUSY: every worker is busy")
    fun chat(
        @RequestBody request: AskChatRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<SseEmitter> {
        val session = sessionService.validate(request.sessionId, clientIpResolver.resolve(httpRequest))
        chatService.checkCanAsk(session, request.message)

        return startStream(session, AskMode.CHAT, CHAT_STREAM_TIMEOUT_MS, keepalive = false) { stream ->
            val answer = chatService.answer(session, request.message) { step -> stream.step(AskStepEvent.from(step)) }
            if (!stream.answer(AskAnswerEvent.from(answer))) {
                logger.info("Chat answer ready after its stream closed: turnId={}", answer.turnId)
            }
        }
    }

    /**
     * Checks a job description against the profile, as an SSE stream of progress steps, then the
     * report.
     */
    @PostMapping("/fit-check")
    @Operation(
        summary = "Check a job description against the profile (SSE stream)",
        description = "Responds with text/event-stream. Events: `step` {kind: GUARDRAIL | TOOL_CALL | " +
                "COMPOSING | VERIFYING, tool?, args?}, then `report` {turnId, report, toolTrace} or `error` " +
                "{code: BLOCKED | CAPPED | RATE_LIMITED | UPSTREAM | INTERNAL, message, capScope?}, then " +
                "`done` {}. A `:keepalive` comment line comes every 15 s.",
    )
    @ApiResponse(responseCode = "200", description = "The stream; failures after this point are `error` events")
    @ApiResponse(responseCode = "400", description = "Blank or too long job description, or a malformed body")
    @ApiResponse(responseCode = "401", description = "SESSION_EXPIRED: unknown, expired or from another network")
    @ApiResponse(responseCode = "429", description = "RATE_LIMITED: this IP's fit checks for today are used up")
    @ApiResponse(responseCode = "503", description = "BUSY: every worker is busy")
    fun fitCheck(
        @RequestBody request: AskFitCheckRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<SseEmitter> {
        val session = sessionService.validate(request.sessionId, clientIpResolver.resolve(httpRequest))
        fitCheckService.checkCanCheck(session, request.jobDescription)

        // The stream's own end: after it, a report can't be delivered any more.
        val deadline = Instant.now().plusMillis(FIT_STREAM_TIMEOUT_MS)
        return startStream(session, AskMode.FIT_CHECK, FIT_STREAM_TIMEOUT_MS, keepalive = true) { stream ->
            val result = fitCheckService.check(session, request.jobDescription, deadline) { step ->
                stream.step(AskStepEvent.from(step))
            }
            if (!stream.report(AskFitReportEvent.from(result))) {
                logger.info("Fit check report ready after its stream closed: turnId={}", result.turnId)
            }
        }
    }

    /**
     * Public totals for the "How this works" panel.
     */
    @GetMapping("/stats")
    @Operation(
        summary = "Chatbot statistics",
        description = "Answers given, attacks blocked and the share of this month's budget used. Cached for 60 s.",
    )
    @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    fun stats(): AskStatsResponse = AskStatsResponse.from(statsService.current())

    /**
     * Opens a stream that ends after [timeoutMs] and hands [work] to the `askExecutor` pool. [work]
     * runs on a worker thread, sends its steps and ends the stream with its result; [runTurn]
     * turns any failure into an `error` event.
     *
     * @param keepalive whether the stream gets `:keepalive` comments. They start as soon as the
     *   task is queued, not when a worker picks it up: with every worker busy, the task may wait
     *   longer than nginx lets a silent response stay open
     * @throws AskBusyException if the pool is full. Thrown before the stream is returned, so the
     *   visitor still gets a plain 503
     */
    private fun startStream(
        session: AskSession,
        mode: AskMode,
        timeoutMs: Long,
        keepalive: Boolean,
        work: (AskEventStream) -> Unit,
    ): ResponseEntity<SseEmitter> {
        val stream = AskEventStream(SseEmitter(timeoutMs))
        try {
            askExecutor.execute { runTurn(stream, session, mode, work) }
        } catch (ex: TaskRejectedException) {
            logger.warn("{} request refused, all workers busy: sessionId={}", labelOf(mode), session.id)
            throw AskBusyException()
        }
        // Started after the task is accepted, so a refused request has nothing to stop. If the
        // task has already ended the stream, the first comment finds it closed and stops.
        if (keepalive) stream.keepAlive(keepaliveScheduler, KEEPALIVE_INTERVAL)
        return ResponseEntity.ok()
            // nginx would otherwise collect the events and send them all at the end.
            .header(X_ACCEL_BUFFERING, "no")
            .cacheControl(CacheControl.noCache())
            .body(stream.emitter)
    }

    /** Runs on an `askExecutor` thread: runs [work], or ends [stream] with an error event. */
    private fun runTurn(stream: AskEventStream, session: AskSession, mode: AskMode, work: (AskEventStream) -> Unit) {
        val label = labelOf(mode)
        if (stream.isClosed) {
            // The task waited in the queue until the stream timed out. Don't spend money on it.
            logger.info("{} request dropped, its stream closed while it waited: sessionId={}", label, session.id)
            return
        }
        try {
            work(stream)
        } catch (ex: Exception) {
            if (stream.isClosed) {
                // The visitor left or the stream timed out; the turn stopped at its next step.
                logger.info("{} stream closed before the end: sessionId={} error={}", label, session.id, ex.javaClass.simpleName)
                return
            }
            val event = AskErrorEvent.from(ex, mode)
            if (event.code == AskErrorCode.UPSTREAM || event.code == AskErrorCode.INTERNAL) {
                // With the exception and its causes: the AWS error message, e.g. what a
                // ValidationException found wrong in the request, is kept only in the cause.
                logger.error("{} turn failed: sessionId={} code={}", label, session.id, event.code, ex)
            }
            stream.error(event)
        }
    }

    private fun labelOf(mode: AskMode): String = when (mode) {
        AskMode.CHAT -> "Chat"
        AskMode.FIT_CHECK -> "Fit check"
    }

    private companion object {
        /**
         * How long a chat stream may stay open: below nginx's `proxy_read_timeout` of 60 s, so the
         * backend ends the stream first and the turn stops at its next step. The stream ends
         * without `done`: after a timeout nothing more can be sent (see [AskEventStream]).
         */
        const val CHAT_STREAM_TIMEOUT_MS = 55_000L

        /**
         * How long a fit-check stream may stay open. A fit check takes ≈ 15–40 s (the first report
         * in 24 hours waits ≈ 10 s more while Bedrock compiles the report schema). The
         * `:keepalive` comments keep nginx from closing the quiet stream, so this can be longer
         * than nginx's 60 s.
         */
        const val FIT_STREAM_TIMEOUT_MS = 180_000L

        /** How often a fit-check stream gets a `:keepalive` comment: well below nginx's 60 s. */
        val KEEPALIVE_INTERVAL: Duration = Duration.ofSeconds(15)

        const val X_ACCEL_BUFFERING = "X-Accel-Buffering"
    }
}

/** Thrown when the `askExecutor` pool is full. Mapped to HTTP 503 with code `BUSY`. */
class AskBusyException : RuntimeException("All chat workers are busy")