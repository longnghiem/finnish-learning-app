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
import me.longng.finnish_learning_backend.controller.dto.ask.AskSessionResponse
import me.longng.finnish_learning_backend.controller.dto.ask.AskSseEventNames
import me.longng.finnish_learning_backend.controller.dto.ask.AskStatsResponse
import me.longng.finnish_learning_backend.controller.dto.ask.AskStepEvent
import me.longng.finnish_learning_backend.controller.dto.ask.CreateAskSessionRequest
import me.longng.finnish_learning_backend.domain.ask.AskSession
import me.longng.finnish_learning_backend.security.ClientIpResolver
import me.longng.finnish_learning_backend.service.ask.AskSessionService
import me.longng.finnish_learning_backend.service.ask.AskStatsService
import me.longng.finnish_learning_backend.service.ask.ChatService
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
 */
@RestController
@RequestMapping("/api/ask")
@Tag(name = "Ask (recruiter chatbot)", description = "Anonymous chat about Long's professional background")
class AskController(
    private val sessionService: AskSessionService,
    private val chatService: ChatService,
    private val statsService: AskStatsService,
    private val clientIpResolver: ClientIpResolver,
    private val properties: AskProperties,
    @Qualifier(AskExecutorConfig.ASK_EXECUTOR) private val askExecutor: TaskExecutor,
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

        val stream = AskEventStream(SseEmitter(CHAT_STREAM_TIMEOUT_MS))
        try {
            askExecutor.execute { answer(stream, session, request.message) }
        } catch (ex: TaskRejectedException) {
            // Thrown before the stream is returned, so the visitor still gets a plain 503.
            logger.warn("Chat request refused, all workers busy: sessionId={}", session.id)
            throw AskBusyException()
        }
        return ResponseEntity.ok()
            // nginx would otherwise collect the events and send them all at the end.
            .header(X_ACCEL_BUFFERING, "no")
            .cacheControl(CacheControl.noCache())
            .body(stream.emitter)
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

    /** Runs on an `askExecutor` thread: answers the message and ends [stream]. */
    private fun answer(stream: AskEventStream, session: AskSession, message: String) {
        if (stream.isClosed) {
            // The task waited in the queue until the stream timed out. Don't spend money on it.
            logger.info("Chat request dropped, its stream closed while it waited: sessionId={}", session.id)
            return
        }
        try {
            val answer = chatService.answer(session, message) { step -> stream.step(AskStepEvent.from(step)) }
            if (!stream.answer(AskAnswerEvent.from(answer))) {
                logger.info("Chat answer ready after its stream closed: turnId={}", answer.turnId)
            }
        } catch (ex: Exception) {
            if (stream.isClosed) {
                // The visitor left or the stream timed out; the turn stopped at its next step.
                logger.info("Chat stream closed before the answer: sessionId={} error={}", session.id, ex.javaClass.simpleName)
                return
            }
            val event = AskErrorEvent.from(ex)
            if (event.code == AskErrorCode.UPSTREAM || event.code == AskErrorCode.INTERNAL) {
                // With the exception and its causes: the AWS error message, e.g. what a
                // ValidationException found wrong in the request, is kept only in the cause.
                logger.error("Chat turn failed: sessionId={} code={}", session.id, event.code, ex)
            }
            stream.error(event)
        }
    }

    private companion object {
        /**
         * How long a chat stream may stay open: below nginx's `proxy_read_timeout` of 60 s, so the
         * backend ends the stream first and the turn stops at its next step. The stream ends
         * without `done`: after a timeout nothing more can be sent (see [AskEventStream]).
         */
        const val CHAT_STREAM_TIMEOUT_MS = 55_000L

        const val X_ACCEL_BUFFERING = "X-Accel-Buffering"
    }
}

/** Thrown when the `askExecutor` pool is full. Mapped to HTTP 503 with code `BUSY`. */
class AskBusyException : RuntimeException("All chat workers are busy")