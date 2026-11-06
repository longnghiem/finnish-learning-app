package me.longng.finnish_learning_backend.controller

import me.longng.finnish_learning_backend.TestcontainersConfiguration
import me.longng.finnish_learning_backend.controller.dto.ask.AskChatRequest
import me.longng.finnish_learning_backend.domain.ask.AskSession
import me.longng.finnish_learning_backend.service.ask.AskProgressListener
import me.longng.finnish_learning_backend.service.ask.AskSessionService
import me.longng.finnish_learning_backend.service.ask.AskStep
import me.longng.finnish_learning_backend.service.ask.ChatAnswer
import me.longng.finnish_learning_backend.service.ask.ChatService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The chat stream through a real web server (Tomcat on a random port) and the real `askExecutor`.
 * MockMvc can't show this: that each event reaches the client when it is sent, not all at the end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
@ExtendWith(OutputCaptureExtension::class)
class AskControllerStreamingTest {

    companion object {
        private val SESSION_ID: UUID = UUID.fromString("7d1f6f3e-3b5e-4c2a-9a52-0c1f2b3c4d5e")
        private const val MESSAGE = "Has Long used Kafka?"
        private const val GUARDRAIL_STEP = """data:{"kind":"GUARDRAIL"}"""
        private const val VISITOR_LEFT_LOG = "Streaming response ended, the client disconnected"
    }

    @LocalServerPort
    private var port: Int = 0

    @MockitoBean
    private lateinit var sessionService: AskSessionService

    @MockitoBean
    private lateinit var chatService: ChatService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val session = AskSession(
        id = SESSION_ID,
        ipHash = "a".repeat(AskSession.IP_HASH_LENGTH),
        createdAt = Instant.parse("2098-10-15T09:00:00Z"),
        expiresAt = Instant.parse("2098-10-15T10:00:00Z"),
    )

    @Test
    fun testChat_StepArrivesBeforeTheAnswer() {
        val stepRead = CountDownLatch(1)
        whenever(sessionService.validate(eq(SESSION_ID), any())).thenReturn(session)
        whenever(chatService.answer(eq(session), eq(MESSAGE), any())).thenAnswer { call ->
            call.getArgument<AskProgressListener>(2).onStep(AskStep.GuardrailPassed)
            // Answer only after the client has read the step. If the step were held back until
            // the end of the response, this would wait in vain and the stream would end in error.
            check(stepRead.await(10, TimeUnit.SECONDS)) { "The step didn't reach the client in time" }
            ChatAnswer(turnId = 42, text = "Yes.", toolTrace = emptyList())
        }

        val response = HttpClient.newHttpClient().send(chatRequest(), HttpResponse.BodyHandlers.ofLines())

        val lines = mutableListOf<String>()
        response.body().use { stream ->
            stream.forEach { line ->
                lines += line
                if (line == GUARDRAIL_STEP) stepRead.countDown()
            }
        }

        assertEquals(200, response.statusCode())
        assertEquals("no", response.headers().firstValue("X-Accel-Buffering").orElse(null))
        assertEquals(
            listOf(
                "event:step", GUARDRAIL_STEP, "",
                "event:answer", """data:{"turnId":42,"text":"Yes.","toolTrace":[]}""", "",
                "event:done", "data:{}", "",
            ),
            lines,
        )
    }

    @Test
    fun testChat_VisitorLeavesMidStream_NoErrorLogged(output: CapturedOutput) {
        val visitorLeft = CountDownLatch(1)
        val turnStopped = CountDownLatch(1)
        whenever(sessionService.validate(eq(SESSION_ID), any())).thenReturn(session)
        whenever(chatService.answer(eq(session), eq(MESSAGE), any())).thenAnswer { call ->
            val listener = call.getArgument<AskProgressListener>(2)
            listener.onStep(AskStep.GuardrailPassed)
            check(visitorLeft.await(10, TimeUnit.SECONDS)) { "The client didn't close the stream" }
            try {
                // The first writes after the client has gone may still succeed: they only fill the
                // socket's buffer. One of the next ones fails once the server sees the reset.
                repeat(200) {
                    listener.onStep(AskStep.Composing)
                    Thread.sleep(25)
                }
                error("The server never noticed that the visitor left")
            } finally {
                turnStopped.countDown()
            }
        }

        val response = HttpClient.newHttpClient().send(chatRequest(), HttpResponse.BodyHandlers.ofLines())
        response.body().use { stream ->
            // Read the first step, then leave, like a visitor closing the tab.
            stream.iterator().let { lines -> while (lines.next() != GUARDRAIL_STEP) Unit }
        }
        visitorLeft.countDown()

        check(turnStopped.await(15, TimeUnit.SECONDS)) { "The turn didn't stop" }
        // The servlet container reports the broken connection on its own thread, a moment later.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (VISITOR_LEFT_LOG !in output.out && System.nanoTime() < deadline) Thread.sleep(50)

        assertFalse("An unexpected error occurred" in output.out, "a visitor leaving isn't an error")
        assertFalse("Chat turn failed" in output.out, "a visitor leaving isn't a failed turn")
        assertTrue(VISITOR_LEFT_LOG in output.out, "the broken connection reached the exception handler")
    }

    private fun chatRequest(): HttpRequest =
        HttpRequest.newBuilder(URI("http://localhost:$port/api/ask/chat"))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(AskChatRequest(SESSION_ID, MESSAGE))))
            .build()
}