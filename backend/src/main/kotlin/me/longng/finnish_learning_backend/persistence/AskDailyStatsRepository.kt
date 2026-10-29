package me.longng.finnish_learning_backend.persistence

import me.longng.finnish_learning_backend.domain.ask.DailyAskStats
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Stores [DailyAskStats]: one row per Europe/Helsinki calendar day with the recruiter chatbot's
 * counters and money. Rows hold no personal data, are created on first use and kept indefinitely.
 *
 * The money columns back the budget cap with a reserve-then-settle scheme. The exact cost of a
 * request is only known when it ends, so a request first reserves its worst-case cost and settles
 * it afterwards:
 *
 * 1. [acquireBudgetLock], so that only one request at a time checks and reserves;
 * 2. [sumSpentAndReservedUsd] (or [findByDay]) to check the caps, then [addReservation];
 * 3. run the request;
 * 4. [settleReservation] with the actual cost, and [incrementCounters].
 *
 * While a request runs, its reservation counts as used, so requests running at the same time can't
 * overspend a cap that only has room for one of them.
 */
interface AskDailyStatsRepository {

    /**
     * Makes concurrent budget checks wait for each other: takes a PostgreSQL transaction-level
     * advisory lock, which a second caller waits for until the first caller's transaction ends.
     *
     * Call it at the start of the transaction that checks the caps and calls [addReservation], so
     * that no other request can reserve in between.
     *
     * @throws IllegalStateException if no transaction is active: the lock would be released at once
     *   and protect nothing
     */
    fun acquireBudgetLock()

    /**
     * Sets [amountUsd] aside for a request that is about to run: adds it to the day's reserved
     * total, creating the day's row if needed. Until [settleReservation] is called, the amount
     * counts as used in [sumSpentAndReservedUsd].
     */
    fun addReservation(day: LocalDate, amountUsd: BigDecimal)

    /**
     * Replaces a reservation with the actual cost once a request has ended: removes [reservedUsd]
     * from the day's reserved total and adds [actualUsd] to its spent total.
     *
     * [day] is the day the reservation was made on, not today: a request that starts just before
     * midnight settles on the day it reserved on. [actualUsd] may be higher than [reservedUsd]; the
     * actual cost is recorded as it is.
     *
     * @throws IllegalStateException if [day] has no row, so nothing was reserved on it
     * @throws org.springframework.dao.DataIntegrityViolationException if [reservedUsd] is more
     *   than the day's reserved total: settling more than was reserved is a bug in the caller
     */
    fun settleReservation(day: LocalDate, reservedUsd: BigDecimal, actualUsd: BigDecimal)

    /**
     * Adds to the day's activity counters, creating the day's row if needed. Pass only the
     * counters that changed, e.g. `answers = 1` after a chat answer or `blocked = 1` after the
     * guardrail refused a message.
     */
    fun incrementCounters(day: LocalDate, answers: Int = 0, fitChecks: Int = 0, blocked: Int = 0)

    /** The totals of one day, or null if the chatbot wasn't used that day. */
    fun findByDay(day: LocalDate): DailyAskStats?

    /**
     * How much budget the days [from]..[to] (both inclusive) have used: the money spent plus the
     * money still reserved by running requests. For the monthly cap, pass the first and last day
     * of the month. Returns zero if none of the days has a row.
     */
    fun sumSpentAndReservedUsd(from: LocalDate, to: LocalDate): BigDecimal

    /**
     * The rows for the days [from]..[to] (both inclusive), oldest first, for the statistics
     * views. Days without activity have no row and are missing from the list.
     */
    fun findByDayRange(from: LocalDate, to: LocalDate): List<DailyAskStats>

    /**
     * Sets the reserved total of every day back to zero and returns how many days had a
     * reservation. The spent totals stay.
     *
     * Only safe when no request can still be running, i.e. at startup
     * ([me.longng.finnish_learning_backend.service.ask.AskStartupCleanup]): a running request
     * would later settle a reservation that no longer exists.
     */
    fun clearReservations(): Int
}