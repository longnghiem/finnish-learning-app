package me.longng.finnish_learning_backend.service.groq

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-user daily request counter held entirely in memory.
 */
@Component
class DailyQuotaTracker(
    private val clock: Clock = Clock.system(HELSINKI_ZONE),
) {
    private data class Counter(val date: LocalDate, val count: Int)

    private val counters = ConcurrentHashMap<Int, Counter>()

    /**
     * Increments the counter for [userId] for the current
     * Europe/Helsinki calendar day.
     *
     * @param userId      The user attempting an evaluation.
     * @param dailyLimit  Maximum allowed requests per user per Helsinki day. Must be positive.
     * @return `true` when the request fits within [dailyLimit], `false` when the
     *         user has already reached the limit (no further increment is performed).
     */
    fun tryConsume(userId: Int, dailyLimit: Int): Boolean {
        require(dailyLimit > 0) { "dailyLimit must be positive, got $dailyLimit" }
        val today = LocalDate.now(clock)
        val updated = counters.compute(userId) { _, existing ->
            when {
                existing == null || existing.date != today -> Counter(today, 1)
                existing.count >= dailyLimit -> existing
                else -> existing.copy(count = existing.count + 1)
            }
        }!!
        return updated.count <= dailyLimit && updated.date == today
    }

    companion object {
        private val HELSINKI_ZONE: ZoneId = ZoneId.of("Europe/Helsinki")
    }
}