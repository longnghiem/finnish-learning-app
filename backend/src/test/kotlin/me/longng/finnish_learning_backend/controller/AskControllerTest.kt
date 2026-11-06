package me.longng.finnish_learning_backend.controller

import jakarta.servlet.AsyncEvent
import me.longng.finnish_learning_backend.TestcontainersConfiguration
import me.longng.finnish_learning_backend.config.AskExecutorConfig
import me.longng.finnish_learning_backend.controller.dto.ask.AskChatRequest
import me.longng.finnish_learning_backend.controller.dto.ask.AskFitCheckRequest
import me.longng.finnish_learning_backend.controller.dto.ask.CreateAskSessionRequest
import me.longng.finnish_learning_backend.domain.ask.AskSession
import me.longng.finnish_learning_backend.domain.ask.ToolCall
import me.longng.finnish_learning_backend.service.ask.AskBudgetExhaustedException
import me.longng.finnish_learning_backend.service.ask.AskConversationLimitException
import me.longng.finnish_learning_backend.service.ask.AskInputBlockedException
import me.longng.finnish_learning_backend.service.ask.AskModelUnavailableException
import me.longng.finnish_learning_backend.service.ask.AskProgressListener
import me.longng.finnish_learning_backend.service.ask.AskRateLimitedException
import me.longng.finnish_learning_backend.service.ask.AskSessionInvalidException
import me.longng.finnish_learning_backend.service.ask.AskSessionService
import me.longng.finnish_learning_backend.service.ask.AskStats
import me.longng.finnish_learning_backend.service.ask.AskStatsService
import me.longng.finnish_learning_backend.service.ask.AskStep
import me.longng.finnish_learning_backend.service.ask.ChatAnswer
import me.longng.finnish_learning_backend.service.ask.ChatService
import me.longng.finnish_learning_backend.service.ask.FitCheckResult
import me.longng.finnish_learning_backend.service.ask.FitCheckService
import me.longng.finnish_learning_backend.service.ask.FitEvidence
import me.longng.finnish_learning_backend.service.ask.FitReport
import me.longng.finnish_learning_backend.service.ask.FitRequirement
import me.longng.finnish_learning_backend.service.ask.FitVerdict
import me.longng.finnish_learning_backend.service.ask.TurnstileVerificationException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.core.task.TaskExecutor
import org.springframework.core.task.TaskRejectedException
import org.springframework.http.MediaType
import org.springframework.mock.web.MockAsyncContext
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [AskController] in the real Spring context (security rules, exception handler, JSON), with the
 * services mocked. The `askExecutor` mock runs each task at once on the test thread, so the stream
 * is complete when the test reads it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@ExtendWith(OutputCaptureExtension::class)
class AskControllerTest {

    companion object {
        private val SESSION_ID: UUID = UUID.fromString("7d1f6f3e-3b5e-4c2a-9a52-0c1f2b3c4d5e")
        private const val MESSAGE = "Has Long used Kafka?"
        private const val JD = "Senior Backend Engineer. Must have: Kafka in production."
    }

    @MockitoBean
    private lateinit var sessionService: AskSessionService

    @MockitoBean
    private lateinit var chatService: ChatService

    @MockitoBean
    private lateinit var fitCheckService: FitCheckService

    @MockitoBean
    private lateinit var statsService: AskStatsService

    @MockitoBean
    private lateinit var keepaliveScheduler: AskKeepaliveScheduler

    @MockitoBean(name = AskExecutorConfig.ASK_EXECUTOR)
    private lateinit var askExecutor: TaskExecutor

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val session = AskSession(
        id = SESSION_ID,
        ipHash = "a".repeat(AskSession.IP_HASH_LENGTH),
        createdAt = Instant.parse("2098-10-15T09:00:00Z"),
        expiresAt = Instant.parse("2098-10-15T10:00:00Z"),
    )

    @BeforeEach
    fun setup() {
        whenever(sessionService.validate(eq(SESSION_ID), any())).thenReturn(session)
        doAnswer { it.getArgument<Runnable>(0).run() }.whenever(askExecutor).execute(any())
    }

    private fun postChat(message: String = MESSAGE): ResultActionsDsl =
        mockMvc.post("/api/ask/chat") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(AskChatRequest(SESSION_ID, message))
        }

    private fun postFitCheck(jobDescription: String = JD): ResultActionsDsl =
        mockMvc.post("/api/ask/fit-check") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(AskFitCheckRequest(SESSION_ID, jobDescription))
        }

    /** The whole SSE body of a request that started streaming. */
    private fun streamOf(result: ResultActionsDsl): String {
        val started = result.andExpect { request { asyncStarted() } }.andReturn()
        return mockMvc.perform(asyncDispatch(started)).andReturn().response.contentAsString
    }

    /** [ChatService.answer] fails with [ex]. */
    private fun answerFails(ex: Exception) {
        whenever(chatService.answer(eq(session), eq(MESSAGE), any())).thenThrow(ex)
    }

    // ------------------------------------------------------------------ POST /session

    @Test
    fun testCreateSession_Success() {
        whenever(sessionService.create(eq("token-1"), any())).thenReturn(session)

        mockMvc.post("/api/ask/session") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreateAskSessionRequest("token-1"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.sessionId") { value(SESSION_ID.toString()) }
            jsonPath("$.expiresAt") { value("2098-10-15T10:00:00Z") }
            jsonPath("$.maxMessages") { value(10) }
        }
    }

    @Test
    fun testCreateSession_BlankToken400() {
        mockMvc.post("/api/ask/session") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"turnstileToken":" "}"""
        }.andExpect {
            status { isBadRequest() }
        }
        verify(sessionService, never()).create(any(), any())
    }

    @Test
    fun testCreateSession_TurnstileFailed403() {
        whenever(sessionService.create(any(), any()))
            .thenThrow(AskSessionInvalidException(AskSessionInvalidException.Reason.TURNSTILE_FAILED))

        mockMvc.post("/api/ask/session") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreateAskSessionRequest("bad"))
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("TURNSTILE_FAILED") }
            jsonPath("$.message") { value("The bot check failed. Please try again.") }
        }
    }

    @Test
    fun testCreateSession_TurnstileUnavailable502() {
        whenever(sessionService.create(any(), any())).thenThrow(TurnstileVerificationException("Cloudflare: internal-error"))

        mockMvc.post("/api/ask/session") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreateAskSessionRequest("token-1"))
        }.andExpect {
            status { isBadGateway() }
            jsonPath("$.code") { value("UPSTREAM") }
        }
    }

    @Test
    fun testCreateSession_RateLimited429() {
        whenever(sessionService.create(any(), any()))
            .thenThrow(AskRateLimitedException(AskRateLimitedException.Kind.SESSIONS_PER_HOUR, 5))

        mockMvc.post("/api/ask/session") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreateAskSessionRequest("token-1"))
        }.andExpect {
            status { isTooManyRequests() }
            jsonPath("$.code") { value("RATE_LIMITED") }
            jsonPath("$.message") { value("You have started 5 new conversations per hour. Please try again in an hour.") }
        }
    }

    // ------------------------------------------------------------------ POST /chat

    @Test
    fun testChat_StreamsStepsThenAnswer() {
        val read = ToolCall("get_entry", mapOf("id" to "experience/acme-backend"), isError = false)
        whenever(chatService.answer(eq(session), eq(MESSAGE), any())).thenAnswer { call ->
            val listener = call.getArgument<AskProgressListener>(2)
            listener.onStep(AskStep.GuardrailPassed)
            listener.onStep(AskStep.Composing)
            listener.onStep(AskStep.ToolCalled(read.tool, read.args))
            listener.onStep(AskStep.Composing)
            ChatAnswer(turnId = 42, text = "Yes, at Acme.", toolTrace = listOf(read))
        }

        val result = postChat()
        val started = result.andReturn()
        val body = streamOf(result)

        assertEquals(
            "event:step\ndata:{\"kind\":\"GUARDRAIL\"}\n\n" +
                    "event:step\ndata:{\"kind\":\"COMPOSING\"}\n\n" +
                    "event:step\ndata:{\"kind\":\"TOOL_CALL\",\"tool\":\"get_entry\",\"args\":{\"id\":\"experience/acme-backend\"}}\n\n" +
                    "event:step\ndata:{\"kind\":\"COMPOSING\"}\n\n" +
                    "event:answer\ndata:{\"turnId\":42,\"text\":\"Yes, at Acme.\",\"toolTrace\":" +
                    "[{\"tool\":\"get_entry\",\"args\":{\"id\":\"experience/acme-backend\"},\"isError\":false}]}\n\n" +
                    "event:done\ndata:{}\n\n",
            body,
        )
        assertEquals("no", started.response.getHeader("X-Accel-Buffering"))
        assertEquals("no-cache", started.response.getHeader("Cache-Control"))
        assertTrue(started.response.contentType!!.startsWith(MediaType.TEXT_EVENT_STREAM_VALUE))
    }

    @Test
    fun testChat_ExpiredSession401() {
        whenever(sessionService.validate(eq(SESSION_ID), any()))
            .thenThrow(AskSessionInvalidException(AskSessionInvalidException.Reason.SESSION_EXPIRED))

        postChat().andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("SESSION_EXPIRED") }
        }
        verify(askExecutor, never()).execute(any())
    }

    @Test
    fun testChat_ErrorReadableWithEventStreamAccept() {
        // The frontend asks for text/event-stream; an error before the stream must still be JSON.
        whenever(sessionService.validate(eq(SESSION_ID), any()))
            .thenThrow(AskSessionInvalidException(AskSessionInvalidException.Reason.SESSION_EXPIRED))

        mockMvc.post("/api/ask/chat") {
            contentType = MediaType.APPLICATION_JSON
            accept = MediaType.TEXT_EVENT_STREAM
            content = objectMapper.writeValueAsString(AskChatRequest(SESSION_ID, MESSAGE))
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("SESSION_EXPIRED") }
        }
    }

    @Test
    fun testChat_TooLong400() {
        whenever(chatService.checkCanAsk(session, "x".repeat(501)))
            .thenThrow(IllegalArgumentException("The message must be at most 500 characters"))

        postChat("x".repeat(501)).andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("The message must be at most 500 characters") }
        }
        verify(askExecutor, never()).execute(any())
    }

    @Test
    fun testChat_MissingSessionId400() {
        mockMvc.post("/api/ask/chat") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"message":"Kafka?"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun testChat_ConversationLimit429() {
        whenever(chatService.checkCanAsk(session, MESSAGE)).thenThrow(AskConversationLimitException(10))

        postChat().andExpect {
            status { isTooManyRequests() }
            jsonPath("$.code") { value("CONVERSATION_LIMIT") }
            jsonPath("$.message") { value("This conversation has reached its limit of 10 messages. Please start a new one.") }
        }
    }

    @Test
    fun testChat_RateLimited429() {
        whenever(chatService.checkCanAsk(session, MESSAGE))
            .thenThrow(AskRateLimitedException(AskRateLimitedException.Kind.CHAT_PER_DAY, 30))

        postChat().andExpect {
            status { isTooManyRequests() }
            jsonPath("$.code") { value("RATE_LIMITED") }
            jsonPath("$.message") { value("You have reached the limit of 30 chat messages per day. Please try again tomorrow.") }
        }
    }

    @Test
    fun testChat_AllWorkersBusy503() {
        doThrow(TaskRejectedException("queue full")).whenever(askExecutor).execute(any())

        postChat().andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("BUSY") }
        }
        verify(chatService, never()).answer(any(), any(), any())
    }

    @Test
    fun testChat_TimeoutWhileQueuedDropsTheTask(output: CapturedOutput) {
        // The pool is full: the task waits instead of running at once.
        var queued: Runnable? = null
        doAnswer { queued = it.getArgument<Runnable>(0) }.whenever(askExecutor).execute(any())
        val started = postChat().andExpect { request { asyncStarted() } }.andReturn()

        // The servlet container reports the timeout, as it would after 55 s.
        val asyncContext = started.request.asyncContext as MockAsyncContext
        asyncContext.listeners.forEach { it.onTimeout(AsyncEvent(asyncContext)) }
        checkNotNull(queued).run()

        verify(chatService, never()).answer(any(), any(), any())
        // The stream ends without `done`, and no JSON error is appended to it.
        mockMvc.perform(asyncDispatch(started)).andReturn().response.let { response ->
            assertEquals(200, response.status)
            assertEquals("", response.contentAsString)
        }
        assertFalse(output.out.contains("An unexpected error occurred"), "a timeout isn't an error")
    }

    @Test
    fun testChat_BlockedEmitsErrorEvent() {
        answerFails(AskInputBlockedException(listOf("JAILBREAK")))

        val body = streamOf(postChat())

        assertEquals(
            "event:error\ndata:{\"code\":\"BLOCKED\",\"message\":\"I can't answer that message. " +
                    "Please ask about Long's work, projects or skills.\"}\n\n" +
                    "event:done\ndata:{}\n\n",
            body,
        )
        assertFalse(body.contains("JAILBREAK"), "the visitor isn't told which category matched")
    }

    @Test
    fun testChat_CappedEmitsErrorEvent() {
        answerFails(AskBudgetExhaustedException(AskBudgetExhaustedException.Scope.MONTHLY))

        val body = streamOf(postChat())

        assertTrue(body.startsWith("event:error\ndata:{\"code\":\"CAPPED\","), body)
        assertTrue(body.contains("\"capScope\":\"MONTHLY\""), body)
        assertTrue(body.endsWith("event:done\ndata:{}\n\n"), body)
    }

    @Test
    fun testChat_UpstreamErrorLoggedWithCause(output: CapturedOutput) {
        val cause = RuntimeException("ValidationException: messages.2.content.0 is blank")
        answerFails(AskModelUnavailableException("Bedrock refused the chat model call (HTTP 400)", cause))

        val body = streamOf(postChat())

        assertTrue(body.startsWith("event:error\ndata:{\"code\":\"UPSTREAM\","), body)
        assertFalse(body.contains("ValidationException"), "no details for the visitor")
        assertTrue(output.out.contains("Chat turn failed: sessionId=$SESSION_ID code=UPSTREAM"), "logged once")
        assertTrue(output.out.contains("messages.2.content.0 is blank"), "with the cause, for the developer")
    }

    @Test
    fun testChat_UnexpectedErrorIsInternal() {
        answerFails(IllegalStateException("bug"))

        val body = streamOf(postChat())

        assertEquals(
            "event:error\ndata:{\"code\":\"INTERNAL\",\"message\":\"Something went wrong. Please try again.\"}\n\n" +
                    "event:done\ndata:{}\n\n",
            body,
        )
    }

    @Test
    fun testChat_NeverLogsTheMessage(output: CapturedOutput) {
        answerFails(IllegalStateException("bug"))

        streamOf(postChat())

        // MockMvc's own dump of the request ("Body = {...}") is test output, not the app's log.
        val logLines = output.out.lines().filterNot { it.trimStart().startsWith("Body =") }
        assertFalse(logLines.any { it.contains(MESSAGE) })
    }

    // ------------------------------------------------------------------ GET /stats and security

    @Test
    fun testStats_PublicNoAuth() {
        whenever(statsService.current()).thenReturn(AskStats(answersTotal = 12, blockedTotal = 3, budgetUsedPercentThisMonth = 7))

        mockMvc.get("/api/ask/stats").andExpect {
            status { isOk() }
            jsonPath("$.answersTotal") { value(12) }
            jsonPath("$.attacksBlockedTotal") { value(3) }
            jsonPath("$.budgetUsedPercentThisMonth") { value(7) }
        }
    }

    @Test
    fun testAdminRoutesStillProtected() {
        // Regression: the new permitAll rule must not open anything outside /api/ask.
        mockMvc.post("/api/cards") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    // ------------------------------------------------------------------ POST /fit-check

    @Test
    fun testFitCheck_StreamsStepsThenReport() {
        val read = ToolCall("get_entry", mapOf("id" to "experience/acme-backend"), isError = false)
        val report = FitReport(
            requirements = listOf(
                FitRequirement(
                    "Kafka in production",
                    FitVerdict.STRONG,
                    listOf(FitEvidence("experience/acme-backend", "Owned the Kafka consumers", entryTitle = "Backend developer, Acme Oy")),
                ),
                FitRequirement("Kubernetes", FitVerdict.GAP, emptyList()),
            ),
            gaps = listOf("Kubernetes"),
            summary = "Strong backend fit.",
            unverifiedQuotesRemoved = 1,
        )
        whenever(fitCheckService.check(eq(session), eq(JD), any(), any())).thenAnswer { call ->
            val listener = call.getArgument<AskProgressListener>(3)
            listener.onStep(AskStep.GuardrailPassed)
            listener.onStep(AskStep.ToolCalled(read.tool, read.args))
            listener.onStep(AskStep.Composing)
            listener.onStep(AskStep.Verifying)
            FitCheckResult(turnId = 43, report = report, toolTrace = listOf(read))
        }

        val result = postFitCheck()
        val started = result.andReturn()
        val body = streamOf(result)

        assertEquals(
            "event:step\ndata:{\"kind\":\"GUARDRAIL\"}\n\n" +
                    "event:step\ndata:{\"kind\":\"TOOL_CALL\",\"tool\":\"get_entry\",\"args\":{\"id\":\"experience/acme-backend\"}}\n\n" +
                    "event:step\ndata:{\"kind\":\"COMPOSING\"}\n\n" +
                    "event:step\ndata:{\"kind\":\"VERIFYING\"}\n\n" +
                    "event:report\ndata:{\"turnId\":43,\"report\":{\"requirements\":[" +
                    "{\"requirement\":\"Kafka in production\",\"verdict\":\"STRONG\",\"evidence\":" +
                    "[{\"entryId\":\"experience/acme-backend\",\"quote\":\"Owned the Kafka consumers\",\"entryTitle\":\"Backend developer, Acme Oy\"}]}," +
                    "{\"requirement\":\"Kubernetes\",\"verdict\":\"GAP\",\"evidence\":[]}]," +
                    "\"gaps\":[\"Kubernetes\"],\"summary\":\"Strong backend fit.\",\"unverifiedQuotesRemoved\":1,\"requirementsLowered\":0}," +
                    "\"toolTrace\":[{\"tool\":\"get_entry\",\"args\":{\"id\":\"experience/acme-backend\"},\"isError\":false}]}\n\n" +
                    "event:done\ndata:{}\n\n",
            body,
        )
        assertEquals("no", started.response.getHeader("X-Accel-Buffering"))
        assertTrue(started.response.contentType!!.startsWith(MediaType.TEXT_EVENT_STREAM_VALUE))
    }

    @Test
    fun testFitCheck_KeepaliveEvery15Seconds() {
        whenever(fitCheckService.check(eq(session), eq(JD), any(), any()))
            .thenReturn(FitCheckResult(turnId = 43, report = FitReport(emptyList(), emptyList(), "No requirements found."), toolTrace = emptyList()))

        streamOf(postFitCheck())

        verify(keepaliveScheduler).every(eq(Duration.ofSeconds(15)), any())
    }

    @Test
    fun testFitCheck_KeepaliveStartsWhileTheTaskIsQueued() {
        // The pool is full: the task waits, and nginx must not close the silent stream meanwhile.
        doAnswer { }.whenever(askExecutor).execute(any())

        postFitCheck().andExpect { request { asyncStarted() } }

        verify(keepaliveScheduler).every(eq(Duration.ofSeconds(15)), any())
        verify(fitCheckService, never()).check(any(), any(), any(), any())
    }

    @Test
    fun testFitCheck_AllWorkersBusy503_NoKeepalive() {
        doThrow(TaskRejectedException("queue full")).whenever(askExecutor).execute(any())

        postFitCheck().andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("BUSY") }
        }
        verify(keepaliveScheduler, never()).every(any(), any())
    }

    @Test
    fun testFitCheck_DeadlineIsTheStreamTimeout() {
        val deadline = argumentCaptor<Instant>()
        whenever(fitCheckService.check(eq(session), eq(JD), deadline.capture(), any()))
            .thenReturn(FitCheckResult(turnId = 43, report = FitReport(emptyList(), emptyList(), "No requirements found."), toolTrace = emptyList()))
        val before = Instant.now()

        streamOf(postFitCheck())

        val after = Instant.now()
        val streamTimeout = Duration.ofSeconds(180)
        assertTrue(deadline.firstValue in before.plus(streamTimeout)..after.plus(streamTimeout), "180 s from the request, like the stream")
    }

    @Test
    fun testChat_NoKeepalive() {
        whenever(chatService.answer(eq(session), eq(MESSAGE), any())).thenReturn(ChatAnswer(turnId = 42, text = "Yes.", toolTrace = emptyList()))

        streamOf(postChat())

        verify(keepaliveScheduler, never()).every(any(), any())
    }

    @Test
    fun testFitCheck_TooLong400() {
        doThrow(IllegalArgumentException("The job description must be at most 6000 characters"))
            .whenever(fitCheckService).checkCanCheck(eq(session), any())

        postFitCheck("x".repeat(6001)).andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("The job description must be at most 6000 characters") }
        }
        verify(askExecutor, never()).execute(any())
    }

    @Test
    fun testFitCheck_PerIpLimit429() {
        doThrow(AskRateLimitedException(AskRateLimitedException.Kind.FIT_CHECKS_PER_DAY, 3))
            .whenever(fitCheckService).checkCanCheck(eq(session), eq(JD))

        postFitCheck().andExpect {
            status { isTooManyRequests() }
            jsonPath("$.code") { value("RATE_LIMITED") }
            jsonPath("$.message") { value("You have reached the limit of 3 fit checks per day. Please try again tomorrow.") }
        }
    }

    @Test
    fun testFitCheck_ExpiredSession401() {
        whenever(sessionService.validate(eq(SESSION_ID), any()))
            .thenThrow(AskSessionInvalidException(AskSessionInvalidException.Reason.SESSION_EXPIRED))

        postFitCheck().andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("SESSION_EXPIRED") }
        }
        verify(fitCheckService, never()).check(any(), any(), any(), any())
    }

    @Test
    fun testFitCheck_BlockedEmitsFitMessage() {
        whenever(fitCheckService.check(eq(session), eq(JD), any(), any())).thenThrow(AskInputBlockedException(listOf("PROMPT_INJECTION")))

        val body = streamOf(postFitCheck())

        assertEquals(
            "event:error\ndata:{\"code\":\"BLOCKED\",\"message\":\"I can't check this text. Please paste only the job description.\"}\n\n" +
                    "event:done\ndata:{}\n\n",
            body,
        )
    }

    @Test
    fun testFitCheck_UpstreamErrorLogged(output: CapturedOutput) {
        whenever(fitCheckService.check(eq(session), eq(JD), any(), any()))
            .thenThrow(AskModelUnavailableException("The fit report was cut off at 1500 tokens"))

        val body = streamOf(postFitCheck())

        assertTrue(body.startsWith("event:error\ndata:{\"code\":\"UPSTREAM\""), body)
        assertTrue(output.out.contains("Fit check turn failed: sessionId=$SESSION_ID code=UPSTREAM"), "logged once")
    }
}