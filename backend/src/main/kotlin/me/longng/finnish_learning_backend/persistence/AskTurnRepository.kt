package me.longng.finnish_learning_backend.persistence

import me.longng.finnish_learning_backend.domain.ask.AskMode
import me.longng.finnish_learning_backend.domain.ask.AskTurn
import me.longng.finnish_learning_backend.domain.ask.TurnCompletion
import me.longng.finnish_learning_backend.domain.ask.TurnFilter
import me.longng.finnish_learning_backend.domain.ask.TurnStatus
import java.time.Instant
import java.util.UUID

/** Stores the [AskTurn]s of the recruiter chatbot. */
interface AskTurnRepository {

    /** Stores a new turn as [TurnStatus.PENDING] and returns its id. */
    fun insertPending(sessionId: UUID, mode: AskMode, userText: String): Long

    /**
     * Records how the turn ended. A turn is completed exactly once.
     *
     * @throws IllegalStateException if the turn doesn't exist or is no longer
     *   [TurnStatus.PENDING]
     */
    fun complete(id: Long, completion: TurnCompletion)

    /** All turns of one session, oldest first. */
    fun findBySession(sessionId: UUID): List<AskTurn>

    /**
     * Counts the turns of one [mode] in a session, whatever their status: a blocked message still
     * counts towards the conversation limit.
     */
    fun countTurns(sessionId: UUID, mode: AskMode): Int

    /**
     * Counts turns of one [mode] created at or after [since] in any session with this IP hash.
     * [TurnStatus.CAPPED] turns don't count: they were refused before any paid call because the
     * budget was full, so they cost nothing and the visitor got nothing.
     */
    fun countForIpSince(ipHash: String, mode: AskMode, since: Instant): Int

    /** One page of turns for the admin view, newest first. */
    fun findPage(filter: TurnFilter, limit: Int, offset: Int): List<AskTurn>

    /**
     * Marks every [TurnStatus.PENDING] turn as [TurnStatus.ERROR] and returns how many there were.
     * Cost, tokens and latency stay as stored: what such a turn really spent is unknown.
     *
     * Only safe when no turn can still be running, i.e. at startup
     * ([me.longng.finnish_learning_backend.service.ask.AskStartupCleanup]).
     */
    fun failPending(): Int
}