package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.domain.ask.TurnStatus
import me.longng.finnish_learning_backend.persistence.AskDailyStatsRepository
import me.longng.finnish_learning_backend.persistence.AskTurnRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.stereotype.Component

/**
 * Cleans up after requests that a restart cut off, once at startup.
 *
 * The problem: a request reserves its worst-case cost before its first paid call and settles it
 * when it ends ([AskTurnRecorder]). If the app stops in between (a deploy that waits too long, a
 * crash, an [OutOfMemoryError] that no `catch (ex: Exception)` catches), the settle never runs.
 * The turn stays [TurnStatus.PENDING] for good, and its reservation stays in the day's reserved
 * total, which [BudgetService] counts as used. So every cut-off request would lower what is left
 * of the monthly cap, for good.
 *
 * Why at startup, and not on a timer: the reservation isn't stored per turn, only as a daily total,
 * so a timer couldn't tell which part of the total belongs to a stuck request and which to one that
 * is still running. At startup the answer is simple, because the backend runs as one instance:
 * nothing can be running yet, so every reservation and every PENDING turn is left over from before.
 *
 * The one-instance assumption is the important limit. With two backends, one starting up would
 * clear the reservations of requests the other is still running. If that ever changes, the
 * reservation must be stored with the turn, and stale turns released one by one.
 *
 * It runs from [afterSingletonsInstantiated]: after every bean exists (so the database migrations
 * have run) and before the web server starts taking requests. A later event such as
 * [ApplicationReadyEvent] comes after the server has started, so a first request could already
 * have reserved money that this would then clear.
 *
 * What a cut-off request really spent (the calls made before the stop) is never recorded: that is
 * at most one request's worth per restart, and accepted.
 */
@Component
class AskStartupCleanup(
    private val turnRepository: AskTurnRepository,
    private val statsRepository: AskDailyStatsRepository,
) : SmartInitializingSingleton {
    private val logger = LoggerFactory.getLogger(AskStartupCleanup::class.java)

    override fun afterSingletonsInstantiated() = cleanUp()

    /**
     * Marks every PENDING turn as ERROR and releases every reservation. Logs at WARN if anything
     * was left over, because that means a restart cut a request off.
     */
    fun cleanUp() {
        val failedTurns = turnRepository.failPending()
        val daysReleased = statsRepository.clearReservations()
        if (failedTurns > 0 || daysReleased > 0) {
            logger.warn(
                "Ask startup cleanup: requests cut off by a restart were found: turnsMarkedError={} daysWithReservationsReleased={}",
                failedTurns,
                daysReleased,
            )
        } else {
            logger.info("Ask startup cleanup: nothing left over from before the restart")
        }
    }
}