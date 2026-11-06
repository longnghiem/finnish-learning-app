package me.longng.finnish_learning_backend.controller

import me.longng.finnish_learning_backend.controller.dto.ask.AskAnswerEvent
import me.longng.finnish_learning_backend.controller.dto.ask.AskErrorEvent
import me.longng.finnish_learning_backend.controller.dto.ask.AskFitReportEvent
import me.longng.finnish_learning_backend.controller.dto.ask.AskSseEventNames
import me.longng.finnish_learning_backend.controller.dto.ask.AskStepEvent
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.time.Duration
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One visitor's SSE stream: progress steps, then exactly one ending (an answer, a fit report or
 * an error), then `done`. Wraps an [SseEmitter] so that the stream ends exactly once, whoever
 * ends it.
 *
 * Three kinds of threads touch one stream:
 * - the worker thread that answers the request calls [step], then [answer], [report] or [error];
 * - the servlet container calls the callbacks: timeout when [SseEmitter]'s timeout has passed,
 *   error when the visitor has left, completion when the response has ended;
 * - for a fit check, the [AskKeepaliveScheduler] thread sends `:keepalive` comments ([keepAlive]).
 *   [SseEmitter] writes one event at a time, so a comment never lands inside another event.
 *
 * [closed] is the one shared flag. The worker ends the stream only if it sets the flag from false
 * to true; [AtomicBoolean.compareAndSet] makes "check and set" one step, so it can't race with a
 * callback. After a timeout nothing more can be sent: Spring marks the emitter complete before it
 * calls the timeout callback. So the visitor's stream simply ends without `done`, and the
 * frontend shows "please try again", as it must anyway when nginx or the network ends a stream.
 *
 * @param emitter the emitter to write to; the controller returns it to Spring MVC
 */
class AskEventStream(val emitter: SseEmitter) {

    private val logger = LoggerFactory.getLogger(AskEventStream::class.java)

    private val closed = AtomicBoolean(false)

    @Volatile
    private var keepalive: ScheduledFuture<*>? = null

    init {
        // After any of these, the worker's next step fails, and that stops the turn.
        emitter.onTimeout { closed.set(true) }
        emitter.onError { closed.set(true) }
        emitter.onCompletion { closed.set(true) }
    }

    /** True once the stream has ended, has timed out, or the visitor has left. */
    val isClosed: Boolean
        get() = closed.get()

    /**
     * Sends a progress step.
     *
     * @throws IllegalStateException if the stream is closed
     * @throws IOException if the visitor has left; the stream is then closed. Both are meant to
     *   reach the caller: there is no point in going on with an answer nobody will read
     */
    fun step(event: AskStepEvent) {
        check(!closed.get()) { "The stream is closed" }
        try {
            send(AskSseEventNames.STEP, event)
        } catch (ex: IOException) {
            // Closed here, not only by the error callback: the container reports the broken
            // connection on its own thread, often after the worker has already handled this.
            closed.set(true)
            throw ex
        }
    }

    /** Ends the stream with [event]. Returns false if it had already ended, and sends nothing. */
    fun answer(event: AskAnswerEvent): Boolean = end(AskSseEventNames.ANSWER, event)

    /** Ends the stream with [event]. Returns false if it had already ended, and sends nothing. */
    fun report(event: AskFitReportEvent): Boolean = end(AskSseEventNames.REPORT, event)

    /** Ends the stream with [event]. Returns false if it had already ended, and sends nothing. */
    fun error(event: AskErrorEvent): Boolean = end(AskSseEventNames.ERROR, event)

    /**
     * Sends a `:keepalive` comment every [interval] until the stream closes. A fit check can be
     * silent for 20 s or more while the report is written, and nginx closes a response that
     * sends nothing for 60 s (`proxy_read_timeout`); every comment resets that timer. Clients
     * ignore comments.
     */
    fun keepAlive(scheduler: AskKeepaliveScheduler, interval: Duration) {
        keepalive = scheduler.every(interval) { sendKeepalive() }
    }

    private fun sendKeepalive() {
        if (closed.get()) {
            stopKeepalive()
            return
        }
        try {
            emitter.send(SseEmitter.event().comment(KEEPALIVE))
        } catch (ex: IOException) {
            // The visitor has left: the worker's next step fails, and that stops the request.
            closed.set(true)
            stopKeepalive()
        } catch (ex: IllegalStateException) {
            // The stream ended at the same moment.
            stopKeepalive()
        }
    }

    private fun stopKeepalive() {
        keepalive?.cancel(false)
    }

    private fun end(name: String, data: Any): Boolean {
        if (!closed.compareAndSet(false, true)) {
            return false
        }
        stopKeepalive()
        sendLast(name, data)
        return true
    }

    /** Sends [data] and `done`, then completes the response. */
    private fun sendLast(name: String, data: Any) {
        try {
            send(name, data)
            send(AskSseEventNames.DONE, emptyMap<String, Any>())
            emitter.complete()
        } catch (ex: IOException) {
            // The visitor has left. No need to complete: the container reports the broken
            // connection to Spring MVC, which ends the response itself.
            logger.debug("Ask stream ended by the visitor: {}", ex.javaClass.simpleName)
        } catch (ex: IllegalStateException) {
            // Either the response ended at the same moment (e.g. it timed out just now), or the
            // event couldn't be written (e.g. it failed to serialize), which leaves the response
            // open. Complete it anyway, so the visitor doesn't wait for the timeout; on a response
            // that has already ended this does nothing. Not ex.message: it contains the event.
            logger.warn("Ask stream ending not sent: cause={}", ex.cause?.javaClass?.simpleName)
            runCatching { emitter.complete() }
        }
    }

    private fun send(name: String, data: Any) {
        emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON))
    }

    private companion object {
        /** The text of the keepalive comment; on the wire it is the line `:keepalive`. */
        const val KEEPALIVE = "keepalive"
    }
}