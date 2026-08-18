package me.longng.finnish_learning_backend.service.bedrock

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-user daily essay-evaluation counter held entirely in memory.
 *
 * The duplication with [me.longng.finnish_learning_backend.service.groq.DailyQuotaTracker]
 * is deliberate — do not merge the two. Keeping two classes gives the two features independent budgets
 */
@Component
class EssayQuotaTracker(
    private val clock: Clock = Clock.system(HELSINKI_ZONE),
) {
    private data class Counter(val date: LocalDate, val count: Int)

    private val counters = ConcurrentHashMap<Int, Counter>()

    /**
     * Records an evaluation attempt by [userId] against the current
     * Europe/Helsinki calendar day and reports whether it is allowed.
     */
    fun tryConsume(userId: Int, dailyLimit: Int): Boolean {
        require(dailyLimit > 0) { "dailyLimit must be positive, got $dailyLimit" }
        val today = LocalDate.now(clock)
        val updated = checkNotNull(
            counters.compute(userId) { _, existing ->
                if (existing == null || existing.date != today) {
                    Counter(today, 1)
                } else {
                    existing.copy(count = existing.count + 1)
                }
            },
        ) { "compute() returns null only if the remapping function does; this one has no null branch" }
        return updated.count <= dailyLimit
    }

    companion object {
        private val HELSINKI_ZONE: ZoneId = ZoneId.of("Europe/Helsinki")
    }
}