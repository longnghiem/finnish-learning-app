package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.config.testAskProperties
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals

/**
 * [AskStatsService] on the in-memory stats repository, with the real [BudgetService] for the
 * month's percentage (budget $5.00 a month).
 */
class AskStatsServiceTest {

    companion object {
        private val HELSINKI: ZoneId = ZoneId.of("Europe/Helsinki")
        private val TODAY: LocalDate = LocalDate.of(2098, 3, 15)
        private val NOON: Instant = TODAY.atTime(12, 0).atZone(HELSINKI).toInstant()
    }

    private val statsRepository = InMemoryAskDailyStatsRepository()
    private val clock = BudgetServiceTest.MutableClock(NOON)
    private val service = AskStatsService(
        statsRepository,
        BudgetService(statsRepository, testAskProperties(), clock),
        clock,
    )

    /** Records [usd] as spent on [day], the way a settled request would. */
    private fun spend(day: LocalDate, usd: String) {
        statsRepository.addReservation(day, BigDecimal(usd))
        statsRepository.settleReservation(day, reservedUsd = BigDecimal(usd), actualUsd = BigDecimal(usd))
    }

    @Test
    fun testCurrent_AddsUpAllDays() {
        statsRepository.incrementCounters(TODAY.minusMonths(2), answers = 4, fitChecks = 1, blocked = 2)
        statsRepository.incrementCounters(TODAY, answers = 3, blocked = 1)
        spend(TODAY.minusMonths(1), "4.00")
        spend(TODAY, "1.00")

        val stats = service.current()

        assertEquals(8, stats.answersTotal, "answers and fit checks of every day")
        assertEquals(3, stats.blockedTotal)
        assertEquals(20, stats.budgetUsedPercentThisMonth, "$1.00 of $5.00; last month's spending doesn't count")
    }

    @Test
    fun testCurrent_NoActivityIsAllZero() {
        assertEquals(AskStats(answersTotal = 0, blockedTotal = 0, budgetUsedPercentThisMonth = 0), service.current())
    }

    @Test
    fun testCurrent_CachedForSixtySeconds() {
        statsRepository.incrementCounters(TODAY, answers = 1)
        service.current()
        statsRepository.incrementCounters(TODAY, answers = 1)

        clock.current = NOON.plus(Duration.ofSeconds(59))
        assertEquals(1, service.current().answersTotal, "still the cached value")

        clock.current = NOON.plus(Duration.ofSeconds(60))
        assertEquals(2, service.current().answersTotal, "read again")
    }

    @Test
    fun testCurrent_PercentNeverAboveHundred() {
        // A request may cost a little more than it reserved, so the month can end just over its cap.
        // BudgetService caps the percentage; this checks the visitor never sees more than 100.
        spend(TODAY, "5.20")

        assertEquals(100, service.current().budgetUsedPercentThisMonth)
    }
}