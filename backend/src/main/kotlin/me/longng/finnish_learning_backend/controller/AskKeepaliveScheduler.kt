package me.longng.finnish_learning_backend.controller

import jakarta.annotation.PreDestroy
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Runs the `:keepalive` comments of open fit-check streams ([AskEventStream.keepAlive]) on one
 * background thread. A comment takes microseconds, so one thread serves every open stream.
 *
 * Owns its executor instead of exposing it as a bean: a [TaskScheduler] or
 * [ScheduledExecutorService] bean would make Spring Boot skip its own scheduler for [Scheduled]
 * jobs, which would then run on this one thread. Stopped when the app stops.
 */
@Component
class AskKeepaliveScheduler {

    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "ask-keepalive").apply { isDaemon = true }
    }

    /** Runs [task] every [interval], first after one [interval], until the returned future is cancelled. */
    fun every(interval: Duration, task: Runnable): ScheduledFuture<*> =
        executor.scheduleAtFixedRate(task, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS)

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
    }
}