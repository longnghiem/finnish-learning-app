package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.TestcontainersConfiguration
import me.longng.finnish_learning_backend.domain.ask.AskMode
import me.longng.finnish_learning_backend.persistence.AskDailyStatsRepository
import me.longng.finnish_learning_backend.persistence.generated.tables.references.ASK_DAILY_STATS
import org.jooq.DSLContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Runs [BudgetService] against a real PostgreSQL, so the advisory lock and the transactions are
 * the real ones. Each method commits its own transaction, so the tests can't roll back: they use
 * days in 2098 and delete them afterwards.
 *
 * The caps are pinned to $0.25 a day and $5.00 a month, whatever the environment overrides.
 */
@SpringBootTest(properties = ["app.ask.budget.daily-usd=0.25", "app.ask.budget.monthly-usd=5.00"])
@Import(TestcontainersConfiguration::class, BudgetServiceTest.ClockConfig::class)
class BudgetServiceTest {

    companion object {
        private val HELSINKI: ZoneId = ZoneId.of("Europe/Helsinki")

        private val TODAY: LocalDate = LocalDate.of(2098, 3, 15)

        /** Noon in Helsinki on [TODAY]. */
        private val NOON: Instant = TODAY.atTime(12, 0).atZone(HELSINKI).toInstant()
    }

    /** A [Clock] the tests can move, shared with every bean of this test's application context. */
    class MutableClock(
        @Volatile var current: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)
        override fun instant(): Instant = current
    }

    @TestConfiguration
    class ClockConfig {
        @Bean
        fun clock(): MutableClock = MutableClock(NOON)
    }

    @Autowired
    private lateinit var budgetService: BudgetService

    @Autowired
    private lateinit var statsRepository: AskDailyStatsRepository

    @Autowired
    private lateinit var clock: MutableClock

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var dsl: DSLContext

    @BeforeEach
    fun resetClock() {
        clock.current = NOON
    }

    @AfterEach
    fun deleteTestDays() {
        dsl.deleteFrom(ASK_DAILY_STATS)
            .where(ASK_DAILY_STATS.DAY.between(LocalDate.of(2098, 1, 1), LocalDate.of(2098, 12, 31)))
            .execute()
    }

    /** Records [amount] as already spent on [day]. */
    private fun spend(day: LocalDate, amount: String) {
        TransactionTemplate(transactionManager).executeWithoutResult {
            statsRepository.addReservation(day, BigDecimal(amount))
            statsRepository.settleReservation(day, reservedUsd = BigDecimal(amount), actualUsd = BigDecimal(amount))
        }
    }

    private fun assertAmount(expected: String, actual: BigDecimal?) {
        assertNotNull(actual)
        assertEquals(0, BigDecimal(expected).compareTo(actual), "expected $expected, got $actual")
    }

    @Test
    fun testReserve_UnderCaps() {
        val reservation = budgetService.reserve(AskMode.CHAT, BigDecimal("0.05"))

        assertEquals(TODAY, reservation.day)
        assertAmount("0.05", reservation.amountUsd)
        assertAmount("0.05", statsRepository.findByDay(TODAY)?.reservedUsd)
    }

    @Test
    fun testReserve_UpToDailyCapAllowed() {
        budgetService.reserve(AskMode.FIT_CHECK, BigDecimal("0.20"))

        // 0.20 + 0.05 is exactly the cap: still allowed.
        budgetService.reserve(AskMode.CHAT, BigDecimal("0.05"))

        assertAmount("0.25", statsRepository.findByDay(TODAY)?.reservedUsd)
    }

    @Test
    fun testReserve_DailyCapHit() {
        budgetService.reserve(AskMode.FIT_CHECK, BigDecimal("0.20"))

        val error = assertThrows<AskBudgetExhaustedException> { budgetService.reserve(AskMode.CHAT, BigDecimal("0.06")) }

        assertEquals(AskBudgetExhaustedException.Scope.DAILY, error.scope)
        assertAmount("0.20", statsRepository.findByDay(TODAY)?.reservedUsd)
    }

    @Test
    fun testReserve_EstimateAboveDailyCapRefused() {
        // Nothing used yet today, but 0.30 alone is more than the 0.25 cap.
        val error = assertThrows<AskBudgetExhaustedException> { budgetService.reserve(AskMode.FIT_CHECK, BigDecimal("0.30")) }

        assertEquals(AskBudgetExhaustedException.Scope.DAILY, error.scope)
        assertNull(statsRepository.findByDay(TODAY), "nothing may be reserved")
    }

    @Test
    fun testReserve_MonthlyCapHit() {
        spend(TODAY.withDayOfMonth(1), "4.90")

        val error = assertThrows<AskBudgetExhaustedException> { budgetService.reserve(AskMode.CHAT, BigDecimal("0.11")) }
        budgetService.reserve(AskMode.CHAT, BigDecimal("0.10"))

        assertEquals(AskBudgetExhaustedException.Scope.MONTHLY, error.scope)
    }

    @Test
    fun testReserve_MonthlyReportedWhenBothCapsFull() {
        spend(TODAY.withDayOfMonth(1), "4.80")
        budgetService.reserve(AskMode.FIT_CHECK, BigDecimal("0.20"))

        val error = assertThrows<AskBudgetExhaustedException> { budgetService.reserve(AskMode.CHAT, BigDecimal("0.06")) }

        assertEquals(AskBudgetExhaustedException.Scope.MONTHLY, error.scope)
    }

    @Test
    fun testReserve_PreviousMonthDoesNotCount() {
        spend(TODAY.withDayOfMonth(1).minusDays(1), "4.99")

        budgetService.reserve(AskMode.FIT_CHECK, BigDecimal("0.20"))
    }

    @Test
    fun testReserve_NewHelsinkiDayResets() {
        // 23:59 in Helsinki is still 21:59 in UTC: the day must follow Helsinki.
        clock.current = TODAY.atTime(23, 59).atZone(HELSINKI).toInstant()
        val lateReservation = budgetService.reserve(AskMode.CHAT, BigDecimal("0.25"))
        assertThrows<AskBudgetExhaustedException> { budgetService.reserve(AskMode.CHAT, BigDecimal("0.01")) }

        clock.current = TODAY.plusDays(1).atStartOfDay(HELSINKI).toInstant()
        val nextDayReservation = budgetService.reserve(AskMode.CHAT, BigDecimal("0.25"))
        // Settled after midnight, but on the day it was reserved on.
        budgetService.settle(lateReservation, BigDecimal("0.10"))

        assertEquals(TODAY.plusDays(1), nextDayReservation.day)
        assertAmount("0.10", statsRepository.findByDay(TODAY)?.spentUsd)
        assertAmount("0", statsRepository.findByDay(TODAY)?.reservedUsd)
    }

    @Test
    fun testSettle_MovesReservedToSpent() {
        val reservation = budgetService.reserve(AskMode.CHAT, BigDecimal("0.05"))

        budgetService.settle(reservation, BigDecimal("0.011"))

        val stats = statsRepository.findByDay(TODAY)
        assertAmount("0", stats?.reservedUsd)
        assertAmount("0.011", stats?.spentUsd)
    }

    @Test
    fun testSettle_ActualAboveReservationRecorded() {
        val reservation = budgetService.reserve(AskMode.CHAT, BigDecimal("0.01"))

        budgetService.settle(reservation, BigDecimal("0.0125"))

        assertAmount("0.0125", statsRepository.findByDay(TODAY)?.spentUsd)
        assertAmount("0", statsRepository.findByDay(TODAY)?.reservedUsd)
    }

    @Test
    fun testRelease_ClearsReservation() {
        val reservation = budgetService.reserve(AskMode.CHAT, BigDecimal("0.05"))

        budgetService.release(reservation)

        val stats = statsRepository.findByDay(TODAY)
        assertAmount("0", stats?.reservedUsd)
        assertAmount("0", stats?.spentUsd)
    }

    @Test
    fun testSettle_TwiceFails() {
        val other = budgetService.reserve(AskMode.CHAT, BigDecimal("0.05"))
        val reservation = budgetService.reserve(AskMode.CHAT, BigDecimal("0.05"))
        budgetService.settle(reservation, BigDecimal("0.01"))

        assertThrows<IllegalStateException> { budgetService.settle(reservation, BigDecimal("0.01")) }
        assertThrows<IllegalStateException> { budgetService.release(reservation) }

        // The other request's reservation is untouched.
        assertAmount(other.amountUsd.toPlainString(), statsRepository.findByDay(TODAY)?.reservedUsd)
    }

    @Test
    fun testReserve_RoundsToStoredScale() {
        val reservation = budgetService.reserve(AskMode.CHAT, BigDecimal("0.123456789"))

        // Settling the rounded amount takes exactly what the database holds.
        budgetService.release(reservation)

        assertEquals(BigDecimal("0.12345679"), reservation.amountUsd)
        assertAmount("0", statsRepository.findByDay(TODAY)?.reservedUsd)
    }

    @Test
    fun testReserve_KeptWhenCallerRollsBack() {
        TransactionTemplate(transactionManager).executeWithoutResult { status ->
            budgetService.reserve(AskMode.CHAT, BigDecimal("0.05"))
            status.setRollbackOnly()
        }

        assertAmount("0.05", statsRepository.findByDay(TODAY)?.reservedUsd)
    }

    @Test
    fun testReserve_ConcurrentRequestsDoNotOvershoot() {
        // 10 requests at once, room for 3: 3 × 0.08 = 0.24 fits in 0.25, a 4th would not.
        val threads = 10
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threads)
        val results = try {
            val futures = List(threads) {
                executor.submit<Result<Reservation>> {
                    start.await()
                    runCatching { budgetService.reserve(AskMode.FIT_CHECK, BigDecimal("0.08")) }
                }
            }
            start.countDown()
            futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        assertEquals(3, results.count { it.isSuccess })
        results.mapNotNull { it.exceptionOrNull() }.forEach { error ->
            assertEquals(AskBudgetExhaustedException.Scope.DAILY, assertIs<AskBudgetExhaustedException>(error).scope)
        }
        assertAmount("0.24", statsRepository.findByDay(TODAY)?.reservedUsd)
    }

    @Test
    fun testUsage_CountsSpentAndReserved() {
        spend(TODAY.withDayOfMonth(1), "1.00")
        budgetService.reserve(AskMode.FIT_CHECK, BigDecimal("0.25"))

        val usage = budgetService.usage()

        assertAmount("0.25", usage.dailyUsedUsd)
        assertAmount("1.25", usage.monthlyUsedUsd)
        assertEquals(25, usage.monthlyPercent)
    }

    @Test
    fun testReserve_NonPositiveEstimateRejected() {
        assertThrows<IllegalArgumentException> { budgetService.reserve(AskMode.CHAT, BigDecimal.ZERO) }
    }
}