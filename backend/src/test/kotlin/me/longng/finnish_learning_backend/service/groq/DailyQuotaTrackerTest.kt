package me.longng.finnish_learning_backend.service.groq

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DailyQuotaTrackerTest {

    companion object {
        private val HELSINKI: ZoneId = ZoneId.of("Europe/Helsinki")

        /** Fixed reference moment: mid-morning, well away from either day boundary. */
        private val NOON_HELSINKI: Instant =
            LocalDate.of(2026, 1, 15).atTime(LocalTime.NOON).atZone(HELSINKI).toInstant()

        private const val USER_A = 1
        private const val USER_B = 2
    }

    /**
     * A [Clock] whose instant can be moved forward, so day-rollover behaviour can be
     * exercised without `Thread.sleep`
     */
    private class MutableClock(
        private var current: Instant,
        private val zone: ZoneId,
    ) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)
        override fun instant(): Instant = current
        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }

    private fun trackerAt(clock: Clock) = DailyQuotaTracker(clock)

    @Test
    fun testTryConsume_FirstCallOfDay() {
        val tracker = trackerAt(Clock.fixed(NOON_HELSINKI, HELSINKI))

        assertTrue(tracker.tryConsume(USER_A, dailyLimit = 50))
    }

    @Test
    fun testTryConsume_UnderLimit() {
        val tracker = trackerAt(Clock.fixed(NOON_HELSINKI, HELSINKI))
        val limit = 50

        repeat(limit) { attempt ->
            assertTrue(
                tracker.tryConsume(USER_A, limit),
                "attempt ${attempt + 1} of $limit should be allowed",
            )
        }
    }

    @Test
    fun testTryConsume_AtLimit() {
        val tracker = trackerAt(Clock.fixed(NOON_HELSINKI, HELSINKI))
        val limit = 3
        repeat(limit) { tracker.tryConsume(USER_A, limit) }

        assertFalse(tracker.tryConsume(USER_A, limit), "the (limit + 1)-th call must be rejected")
        assertFalse(tracker.tryConsume(USER_A, limit), "and every call after it")
        assertFalse(tracker.tryConsume(USER_A, limit), "including once the counter is pinned")
    }

    @Test
    fun testTryConsume_ResetsNextDay() {
        val clock = MutableClock(NOON_HELSINKI, HELSINKI)
        val tracker = trackerAt(clock)
        val limit = 2
        repeat(limit) { tracker.tryConsume(USER_A, limit) }
        assertFalse(tracker.tryConsume(USER_A, limit))

        // Crosses the next Europe/Helsinki midnight.
        clock.advance(Duration.ofHours(24))

        assertTrue(tracker.tryConsume(USER_A, limit), "a new Helsinki day starts a fresh budget")
    }

    @Test
    fun testTryConsume_SameDayAfterHoursDoesNotReset() {
        val clock = MutableClock(NOON_HELSINKI, HELSINKI)
        val tracker = trackerAt(clock)
        val limit = 2
        repeat(limit) { tracker.tryConsume(USER_A, limit) }

        // Still the same Helsinki calendar day — 23:00 rather than midnight.
        clock.advance(Duration.ofHours(11))

        assertFalse(tracker.tryConsume(USER_A, limit), "the budget is per day, not per rolling 24h")
    }

    @Test
    fun testTryConsume_SeparateUsersHaveSeparateCounters() {
        val tracker = trackerAt(Clock.fixed(NOON_HELSINKI, HELSINKI))
        val limit = 2
        repeat(limit) { tracker.tryConsume(USER_A, limit) }
        assertFalse(tracker.tryConsume(USER_A, limit))

        assertTrue(tracker.tryConsume(USER_B, limit), "user B has an untouched budget")
    }

    @Test
    fun testTryConsume_NonPositiveLimit() {
        val tracker = trackerAt(Clock.fixed(NOON_HELSINKI, HELSINKI))

        assertThrows<IllegalArgumentException> { tracker.tryConsume(USER_A, dailyLimit = 0) }
        assertThrows<IllegalArgumentException> { tracker.tryConsume(USER_A, dailyLimit = -1) }
    }

}
