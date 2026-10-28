package me.longng.finnish_learning_backend.config

import me.longng.finnish_learning_backend.controller.AskController
import me.longng.finnish_learning_backend.controller.AskBusyException
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskRejectedException
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

/**
 * The thread pool that answers recruiter chat messages, so the request thread can return the
 * stream at once while the answer is made in the background ([AskController]).
 *
 * The sizes are small on purpose: the server has 1 GB of memory, and every answer costs money.
 * - 2 threads normally. A task that finds both busy waits in the queue.
 * - Up to 10 tasks wait. Only when the queue is full does the pool grow, up to 4 threads.
 * - When 4 threads are busy and 10 tasks wait, a new task is refused with a
 *   [TaskRejectedException], which [AskController] turns into an [AskBusyException] (HTTP 503).
 *
 * On shutdown (a deploy), the pool stops taking tasks and waits up to 60 s for the running ones,
 * so they can store their turn and settle their reservation instead of being cut off.
 */
@Configuration
class AskExecutorConfig {

    @Bean(ASK_EXECUTOR)
    fun askExecutor(): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 2
        queueCapacity = 10
        maxPoolSize = 4
        setThreadNamePrefix("ask-")
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(60)
    }

    companion object {
        /** The bean name, for `@Qualifier`. */
        const val ASK_EXECUTOR = "askExecutor"
    }
}