package me.longng.finnish_learning_backend.controller

import me.longng.finnish_learning_backend.controller.dto.ask.AskAnswerEvent
import me.longng.finnish_learning_backend.controller.dto.ask.AskErrorCode
import me.longng.finnish_learning_backend.controller.dto.ask.AskErrorEvent
import me.longng.finnish_learning_backend.controller.dto.ask.AskFitReportEvent
import me.longng.finnish_learning_backend.controller.dto.ask.AskStepEvent
import me.longng.finnish_learning_backend.service.ask.FitReport
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.time.Duration
import java.util.Collections
import java.util.function.Consumer
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [AskEventStream] without Spring MVC: a [RecordingEmitter] keeps what would have been written,
 * and lets the test fire the callbacks the servlet container would fire.
 */
class AskEventStreamTest {

    /**
     * An [SseEmitter] that records the events and callbacks instead of writing a response. A
     * comment is recorded as `"comment" to ":keepalive"`. Thread-safe, because keepalive comments
     * come from another thread.
     */
    private class RecordingEmitter : SseEmitter(1_000L) {
        val events: MutableList<Pair<String, Any>> = Collections.synchronizedList(mutableListOf())
        var completed = false
        var sendFailure: Exception? = null
        lateinit var timeoutCallback: Runnable
        lateinit var errorCallback: Consumer<Throwable>

        override fun send(builder: SseEmitter.SseEventBuilder) {
            sendFailure?.let { throw it }
            // The builder holds "event:<name>\ndata:", the data object, then "\n\n";
            // a comment is the one text ":<comment>\n\n".
            val parts = builder.build().map { it.data }
            val first = parts.first() as String
            if (first.startsWith(":")) {
                events += "comment" to first.trim()
                return
            }
            val name = first.removePrefix("event:").substringBefore('\n')
            events += name to parts[1]
        }

        override fun complete() {
            completed = true
        }

        override fun onTimeout(callback: Runnable) {
            timeoutCallback = callback
        }

        override fun onError(callback: Consumer<Throwable>) {
            errorCallback = callback
        }

        override fun onCompletion(callback: Runnable) = Unit
    }

    private val emitter = RecordingEmitter()
    private val stream = AskEventStream(emitter)

    private val answer = AskAnswerEvent(turnId = 7, text = "Yes.", toolTrace = emptyList())

    private val scheduler = AskKeepaliveScheduler()

    @AfterEach
    fun stopScheduler() {
        scheduler.shutdown()
    }

    /** Waits up to 2 s for [condition]. */
    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos()
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(5)
        assertTrue(condition(), "condition not met within 2 s")
    }

    private fun comments() = emitter.events.count { it.first == "comment" }

    @Test
    fun testStepThenAnswer_SendsAnswerAndDoneThenCompletes() {
        stream.step(AskStepEvent(AskStepEvent.Kind.GUARDRAIL))

        assertTrue(stream.answer(answer))

        assertEquals(
            listOf("step" to AskStepEvent(AskStepEvent.Kind.GUARDRAIL), "answer" to answer, "done" to emptyMap<String, Any>()),
            emitter.events,
        )
        assertTrue(emitter.completed)
        assertTrue(stream.isClosed)
    }

    @Test
    fun testError_EndsOnlyOnce() {
        val blocked = AskErrorEvent(AskErrorCode.BLOCKED, "No.")

        assertTrue(stream.error(blocked))
        assertFalse(stream.answer(answer), "a second ending is refused")

        assertEquals(listOf("error" to blocked, "done" to emptyMap<String, Any>()), emitter.events)
    }

    @Test
    fun testTimeout_StopsTheTurnAndSendsNothing() {
        emitter.timeoutCallback.run()

        assertTrue(stream.isClosed)
        // The worker's next step fails, so the turn stops instead of paying for an unread answer.
        assertThrows<IllegalStateException> { stream.step(AskStepEvent(AskStepEvent.Kind.COMPOSING)) }
        assertFalse(stream.answer(answer))
        assertEquals(emptyList(), emitter.events, "Spring allows no sends after a timeout")
    }

    @Test
    fun testVisitorLeft_StepThrowsAndEndingIsSkipped() {
        emitter.errorCallback.accept(IOException("Connection reset"))

        assertTrue(stream.isClosed)
        assertThrows<IllegalStateException> { stream.step(AskStepEvent(AskStepEvent.Kind.COMPOSING)) }
        assertFalse(stream.error(AskErrorEvent(AskErrorCode.INTERNAL, "Oops.")))
        assertEquals(emptyList(), emitter.events)
    }

    @Test
    fun testStepSendFailure_ThrowsAndClosesTheStream() {
        emitter.sendFailure = IOException("Broken pipe")

        // A step must fail loudly: it stops the turn.
        assertThrows<IOException> { stream.step(AskStepEvent(AskStepEvent.Kind.COMPOSING)) }
        // Closed at once, before the container's error callback: the worker must see the visitor
        // left, not report a failed turn.
        assertTrue(stream.isClosed)
        assertFalse(stream.error(AskErrorEvent(AskErrorCode.INTERNAL, "Oops.")))
    }

    @Test
    fun testEndingSendFailure_DoesNotThrow() {
        emitter.sendFailure = IOException("Broken pipe")

        // The ending only logs: the turn is already recorded, and there is no one left to tell.
        assertTrue(stream.answer(answer))
        assertFalse(emitter.completed, "the container ends a broken response itself")
    }

    @Test
    fun testEndingNotWritable_StillCompletes() {
        // What SseEmitter throws when an event can't be written, e.g. it fails to serialize.
        emitter.sendFailure = IllegalStateException("Failed to send", RuntimeException("Not serializable"))

        assertTrue(stream.answer(answer))
        assertTrue(emitter.completed, "the visitor isn't left waiting for the timeout")
    }

    @Test
    fun testReport_SendsReportAndDoneThenCompletes() {
        val report = AskFitReportEvent(
            turnId = 8,
            report = FitReport(requirements = emptyList(), gaps = listOf("Kubernetes"), summary = "Partly fits."),
            toolTrace = emptyList(),
        )

        assertTrue(stream.report(report))
        assertFalse(stream.answer(answer), "a second ending is refused")

        assertEquals(listOf("report" to report, "done" to emptyMap<String, Any>()), emitter.events)
        assertTrue(emitter.completed)
    }

    @Test
    fun testKeepAlive_SendsCommentsUntilTheStreamEnds() {
        stream.keepAlive(scheduler, Duration.ofMillis(10))
        waitFor { comments() >= 2 }

        assertTrue(stream.answer(answer))
        val afterEnd = emitter.events.size
        Thread.sleep(60)

        assertEquals(afterEnd, emitter.events.size, "no comment after the stream ended")
        assertEquals(":keepalive", emitter.events.first().second)
        assertEquals(listOf("answer", "done"), emitter.events.takeLast(2).map { it.first })
    }

    @Test
    fun testKeepAlive_VisitorLeftClosesTheStream() {
        emitter.sendFailure = IOException("Broken pipe")

        stream.keepAlive(scheduler, Duration.ofMillis(10))
        waitFor { stream.isClosed }

        // The worker's next step stops the request.
        assertThrows<IllegalStateException> { stream.step(AskStepEvent(AskStepEvent.Kind.COMPOSING)) }
    }

    @Test
    fun testKeepAlive_StopsAfterTimeout() {
        stream.keepAlive(scheduler, Duration.ofMillis(10))
        waitFor { comments() >= 1 }

        emitter.timeoutCallback.run()
        Thread.sleep(30)
        val count = comments()
        Thread.sleep(60)

        assertEquals(count, comments(), "a closed stream gets no more comments")
    }
}