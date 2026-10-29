package me.longng.finnish_learning_backend.persistence

import me.longng.finnish_learning_backend.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class AskDailyStatsRepositoryTest {

    @Autowired
    private lateinit var statsRepository: AskDailyStatsRepository

    // Far-future days, so the tests never meet real rows.
    private val day = LocalDate.of(2099, 3, 15)

    private fun assertAmount(expected: String, actual: BigDecimal?) {
        assertNotNull(actual)
        assertEquals(0, BigDecimal(expected).compareTo(actual), "expected $expected, got $actual")
    }

    @Test
    fun testAddReservation_AddsUp() {
        statsRepository.addReservation(day, BigDecimal("0.0300"))
        statsRepository.addReservation(day, BigDecimal("0.0450"))

        val stats = statsRepository.findByDay(day)

        assertAmount("0.0750", stats?.reservedUsd)
        assertAmount("0", stats?.spentUsd)
    }

    @Test
    fun testSettleReservation_MovesReservedToSpent() {
        statsRepository.addReservation(day, BigDecimal("0.1440"))
        statsRepository.addReservation(day, BigDecimal("0.0300"))

        statsRepository.settleReservation(day, reservedUsd = BigDecimal("0.1440"), actualUsd = BigDecimal("0.0280"))
        val stats = statsRepository.findByDay(day)

        assertAmount("0.0300", stats?.reservedUsd)
        assertAmount("0.0280", stats?.spentUsd)
    }

    @Test
    fun testSettleReservation_ActualAboveReserved() {
        statsRepository.addReservation(day, BigDecimal("0.0100"))

        statsRepository.settleReservation(day, reservedUsd = BigDecimal("0.0100"), actualUsd = BigDecimal("0.0125"))
        val stats = statsRepository.findByDay(day)

        assertAmount("0", stats?.reservedUsd)
        assertAmount("0.0125", stats?.spentUsd)
    }

    @Test
    fun testSettleReservation_MoreThanReservedFails() {
        statsRepository.addReservation(day, BigDecimal("0.0100"))

        assertThrows<DataIntegrityViolationException> {
            statsRepository.settleReservation(day, reservedUsd = BigDecimal("0.0200"), actualUsd = BigDecimal("0.0100"))
        }
    }

    @Test
    fun testSettleReservation_UnknownDayFails() {
        assertThrows<IllegalStateException> {
            statsRepository.settleReservation(day, reservedUsd = BigDecimal.ONE, actualUsd = BigDecimal.ONE)
        }
    }

    @Test
    fun testIncrementCounters_AddsUp() {
        statsRepository.incrementCounters(day, answers = 1)
        statsRepository.incrementCounters(day, answers = 1, fitChecks = 1)
        statsRepository.incrementCounters(day, blocked = 2)

        val stats = statsRepository.findByDay(day)

        assertEquals(2, stats?.answers)
        assertEquals(1, stats?.fitChecks)
        assertEquals(2, stats?.blocked)
    }

    @Test
    fun testFindByDay_UnknownDay() {
        assertNull(statsRepository.findByDay(day))
    }

    @Test
    fun testSumSpentAndReservedUsd_MonthlySum() {
        val firstOfMonth = LocalDate.of(2099, 3, 1)
        val lastOfMonth = LocalDate.of(2099, 3, 31)
        statsRepository.addReservation(firstOfMonth, BigDecimal("1.00"))
        statsRepository.settleReservation(firstOfMonth, reservedUsd = BigDecimal("1.00"), actualUsd = BigDecimal("0.40"))
        statsRepository.addReservation(lastOfMonth, BigDecimal("0.25"))
        statsRepository.addReservation(LocalDate.of(2099, 2, 28), BigDecimal("9.00"))
        statsRepository.addReservation(LocalDate.of(2099, 4, 1), BigDecimal("9.00"))

        // 0.40 spent on the 1st + 0.25 still reserved on the 31st; the neighbouring months don't count
        assertAmount("0.65", statsRepository.sumSpentAndReservedUsd(firstOfMonth, lastOfMonth))
        assertAmount("0", statsRepository.sumSpentAndReservedUsd(LocalDate.of(2099, 5, 1), LocalDate.of(2099, 5, 31)))
    }

    @Test
    fun testFindByDayRange_OldestFirstInclusive() {
        statsRepository.incrementCounters(LocalDate.of(2099, 3, 3), answers = 1)
        statsRepository.incrementCounters(LocalDate.of(2099, 3, 1), answers = 1)
        statsRepository.incrementCounters(LocalDate.of(2099, 3, 5), answers = 1)

        val days = statsRepository.findByDayRange(LocalDate.of(2099, 3, 1), LocalDate.of(2099, 3, 3)).map { it.day }

        assertEquals(listOf(LocalDate.of(2099, 3, 1), LocalDate.of(2099, 3, 3)), days)
    }

    @Test
    fun testAcquireBudgetLock_InsideTransaction() {
        statsRepository.acquireBudgetLock()
        // Taking the same transaction-level lock again in the same transaction must not block.
        statsRepository.acquireBudgetLock()
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun testAcquireBudgetLock_WithoutTransactionFails() {
        assertThrows<IllegalStateException> { statsRepository.acquireBudgetLock() }
    }

    @Test
    fun testClearReservations_AllDaysKeepsSpent() {
        val nextDay = day.plusDays(1)
        statsRepository.addReservation(day, BigDecimal("0.1440"))
        statsRepository.settleReservation(day, reservedUsd = BigDecimal("0.0400"), actualUsd = BigDecimal("0.0310"))
        statsRepository.addReservation(nextDay, BigDecimal("0.0460"))
        statsRepository.incrementCounters(nextDay, answers = 2)

        // "At least": days with reservations committed by other test classes are cleared too.
        assertTrue(statsRepository.clearReservations() >= 2)

        assertAmount("0", statsRepository.findByDay(day)?.reservedUsd)
        assertAmount("0.0310", statsRepository.findByDay(day)?.spentUsd)
        assertAmount("0", statsRepository.findByDay(nextDay)?.reservedUsd)
        assertEquals(2, statsRepository.findByDay(nextDay)?.answers)
        assertEquals(0, statsRepository.clearReservations(), "nothing is reserved any more")
    }

    @Test
    fun testAddReservation_NegativeAmountRejected() {
        assertThrows<IllegalArgumentException> { statsRepository.addReservation(day, BigDecimal("-0.01")) }
    }
}