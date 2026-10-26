package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.domain.ask.AskMode
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Money that [BudgetService.reserve] set aside for one request. Pass it to [BudgetService.settle]
 * or [BudgetService.release] exactly once: settling it twice would take the amount off another
 * request's reservation.
 *
 * @property day the Europe/Helsinki day it was reserved on. It is settled on that day, even when
 *   the request ends after midnight
 * @property amountUsd the reserved amount, exactly as stored
 */
class Reservation internal constructor(
    val mode: AskMode,
    val day: LocalDate,
    val amountUsd: BigDecimal,
) {
    private val open = AtomicBoolean(true)

    /** Marks the reservation as settled. Returns false if it already was. */
    internal fun close(): Boolean = open.compareAndSet(true, false)

    override fun toString(): String = "Reservation(mode=$mode, day=$day, amountUsd=$amountUsd)"
}