package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.config.AskProperties
import me.longng.finnish_learning_backend.domain.ask.AskMode
import me.longng.finnish_learning_backend.persistence.AskDailyStatsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * The chatbot's spending caps: [AskProperties.Budget.dailyUsd] per Europe/Helsinki calendar day
 * and [AskProperties.Budget.monthlyUsd] per calendar month, one pool for chat and fit checks.
 *
 * The cost of a request is only known when it ends, so every request goes through these steps:
 * 1. [reserve] its worst-case cost ([CostCalculator.estimateMax]) before its first billable call.
 *    Refused if a cap has no room for it;
 * 2. the input guardrail, then the model calls. A blocked input makes no model call, so its cost is
 *    the guardrail's alone;
 * 3. [settle] with the actual cost, or [release] if nothing billable ran. Do it in a `finally`
 *    block, so a failed request doesn't keep its reservation.
 *
 * While a request runs, its reservation counts as used, and [reserve] checks and reserves under a
 * database lock, one request at a time. So requests running at the same time can't overspend a cap
 * that only has room for one of them. The actual cost may be higher than the reservation; it is
 * recorded as it is, and later requests see it.
 *
 * Each method runs in its own transaction ([Propagation.REQUIRES_NEW]), even when the caller has
 * one. The lock is then held only while reserving, never during the model calls, and a settlement
 * is kept even when the caller's transaction rolls back.
 */
@Service
class BudgetService(
    private val statsRepository: AskDailyStatsRepository,
    properties: AskProperties,
    private val clock: Clock = Clock.system(HELSINKI_ZONE),
) {
    private val logger = LoggerFactory.getLogger(BudgetService::class.java)

    private val budget = properties.budget

    /**
     * Sets [estimateUsd] aside for a request of [mode] that is about to run.
     *
     * @throws AskBudgetExhaustedException if the day or the month has no room for [estimateUsd].
     *   Nothing is reserved then
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun reserve(mode: AskMode, estimateUsd: BigDecimal): Reservation {
        require(estimateUsd.signum() > 0) { "estimateUsd must be positive" }
        val amountUsd = estimateUsd.roundToUsdScale()
        if (amountUsd > budget.dailyUsd) {
            logger.warn("Estimate above the daily cap, so it can never run: mode={} estimateUsd={}", mode, amountUsd)
        }

        statsRepository.acquireBudgetLock()
        val today = today()
        val dailyUsedUsd = statsRepository.sumSpentAndReservedUsd(today, today)
        val monthlyUsedUsd = statsRepository.sumSpentAndReservedUsd(today.withDayOfMonth(1), today)
        // The month first: when both caps are full, "try again tomorrow" would be wrong.
        val fullCap = when {
            monthlyUsedUsd + amountUsd > budget.monthlyUsd -> AskBudgetExhaustedException.Scope.MONTHLY
            dailyUsedUsd + amountUsd > budget.dailyUsd -> AskBudgetExhaustedException.Scope.DAILY
            else -> null
        }
        if (fullCap != null) {
            logger.info(
                "Budget cap reached: scope={} mode={} estimateUsd={} dailyUsedUsd={} monthlyUsedUsd={}",
                fullCap,
                mode,
                amountUsd,
                dailyUsedUsd,
                monthlyUsedUsd,
            )
            throw AskBudgetExhaustedException(fullCap)
        }

        statsRepository.addReservation(today, amountUsd)
        return Reservation(mode = mode, day = today, amountUsd = amountUsd)
    }

    /**
     * Replaces [reservation] with the request's [actualUsd] cost, on the day it was reserved on.
     * [actualUsd] may be higher than the reservation.
     *
     * If storing fails, the amount stays reserved on its day: the cap errs on the safe side.
     *
     * @throws IllegalStateException if [reservation] was already settled or released
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun settle(reservation: Reservation, actualUsd: BigDecimal) {
        require(actualUsd.signum() >= 0) { "actualUsd must not be negative" }
        settleOnce(reservation, actualUsd.roundToUsdScale())
    }

    /**
     * Gives [reservation] back unused, for a request that failed before any billable call.
     *
     * @throws IllegalStateException if [reservation] was already settled or released
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun release(reservation: Reservation) {
        settleOnce(reservation, BigDecimal.ZERO)
    }

    /** How much of the caps is used today and this month, for the public statistics. */
    @Transactional(readOnly = true)
    fun usage(): BudgetUsage {
        val today = today()
        val monthlyUsedUsd = statsRepository.sumSpentAndReservedUsd(today.withDayOfMonth(1), today)
        val percent = (monthlyUsedUsd * HUNDRED).divide(budget.monthlyUsd, 0, RoundingMode.HALF_UP).toInt()
        return BudgetUsage(
            dailyUsedUsd = statsRepository.sumSpentAndReservedUsd(today, today),
            monthlyUsedUsd = monthlyUsedUsd,
            // A request may cost more than it reserved, so the sum can pass the cap a little.
            monthlyPercent = percent.coerceAtMost(100),
        )
    }

    private fun settleOnce(reservation: Reservation, actualUsd: BigDecimal) {
        check(reservation.close()) { "$reservation was already settled or released" }
        statsRepository.settleReservation(reservation.day, reservedUsd = reservation.amountUsd, actualUsd = actualUsd)
        if (actualUsd > reservation.amountUsd) {
            logger.warn("Actual cost above the reservation: {} actualUsd={}", reservation, actualUsd)
        }
    }

    /** Today in Helsinki, whatever zone [clock] is in. */
    private fun today(): LocalDate = LocalDate.ofInstant(clock.instant(), HELSINKI_ZONE)

    private companion object {
        val HELSINKI_ZONE: ZoneId = ZoneId.of("Europe/Helsinki")
        val HUNDRED = BigDecimal(100)
    }
}

/**
 * How much of the spending caps is used: the money spent plus the money still reserved by
 * running requests.
 *
 * @property monthlyPercent the month's share of [AskProperties.Budget.monthlyUsd], 0 to 100
 */
data class BudgetUsage(
    val dailyUsedUsd: BigDecimal,
    val monthlyUsedUsd: BigDecimal,
    val monthlyPercent: Int,
)

/**
 * Thrown when a request's worst-case cost doesn't fit in a spending cap. Shown to the visitor as
 * "capped", with when to come back.
 *
 * @property scope the cap that is full
 */
class AskBudgetExhaustedException(
    val scope: Scope,
) : RuntimeException("The ${scope.name.lowercase()} budget is used up") {

    /** The caps a [BudgetService] enforces. */
    enum class Scope {
        /** Full until midnight in Helsinki. */
        DAILY,

        /** Full until the first day of next month in Helsinki. */
        MONTHLY,
    }
}