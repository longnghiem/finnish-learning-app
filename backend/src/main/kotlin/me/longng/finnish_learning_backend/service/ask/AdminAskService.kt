package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.config.AskProperties
import me.longng.finnish_learning_backend.domain.ask.AskMode
import me.longng.finnish_learning_backend.domain.ask.AskSession
import me.longng.finnish_learning_backend.domain.ask.AskTurn
import me.longng.finnish_learning_backend.domain.ask.DailyAskStats
import me.longng.finnish_learning_backend.domain.ask.GuardrailOutcome
import me.longng.finnish_learning_backend.domain.ask.TurnFilter
import me.longng.finnish_learning_backend.domain.ask.TurnStatus
import me.longng.finnish_learning_backend.persistence.AskDailyStatsRepository
import me.longng.finnish_learning_backend.persistence.AskSessionRepository
import me.longng.finnish_learning_backend.persistence.AskTurnRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * What the owner sees of the recruiter chatbot in the admin view: the stored turns, one session
 * with all its turns, the daily totals, and deleting a session (also how a visitor's request to
 * delete their conversation is carried out).
 *
 * Days are Europe/Helsinki calendar days, like everywhere else in the chatbot: a turn made at
 * 00:30 Helsinki time belongs to that day, although it is still the day before in UTC.
 *
 * For a blocked turn it also works out which guardrail categories blocked it
 * ([AdminTurn.guardrailBlockedBy]), with the same rule the guardrail uses
 * ([BedrockGuardrailService.blockedBy]). Only the scores are stored, so this uses today's
 * thresholds: if a threshold was changed since, an older turn shows the categories that would
 * block it today.
 */
@Service
class AdminAskService(
    private val turnRepository: AskTurnRepository,
    private val sessionRepository: AskSessionRepository,
    private val statsRepository: AskDailyStatsRepository,
    properties: AskProperties,
    private val clock: Clock = Clock.system(HELSINKI_ZONE),
) {
    private val logger = LoggerFactory.getLogger(AdminAskService::class.java)

    private val guardrailSettings = properties.guardrail

    /**
     * One page of turns, newest first, each with its session's IP hash. Every filter is optional.
     *
     * @param from only turns made on this day or later
     * @param to only turns made on this day or earlier
     * @param limit the page size, 1 to 100
     * @param offset how many of the newest turns to skip, for the next pages
     * @throws IllegalArgumentException if [from] is after [to], or [limit] or [offset] is out of range
     */
    fun listTurns(
        mode: AskMode?,
        status: TurnStatus?,
        from: LocalDate?,
        to: LocalDate?,
        limit: Int,
        offset: Int,
    ): List<AdminTurn> {
        require(from == null || to == null || !from.isAfter(to)) { "from must not be after to" }
        val filter = TurnFilter(
            mode = mode,
            status = status,
            from = from?.let { startOf(it) },
            // The start of the next day, so that the whole of the last day is included.
            to = to?.let { startOf(it.plusDays(1)) },
        )
        val turns = turnRepository.findPage(filter, limit, offset)
        val ipHashes = sessionRepository.findByIds(turns.mapTo(HashSet()) { it.sessionId })
            .associate { it.id to it.ipHash }
        return turns.map { adminTurn(it, ipHashes[it.sessionId]) }
    }

    /**
     * One session with all its turns, oldest first.
     *
     * @throws AskSessionNotFoundException if the session doesn't exist (or was already purged)
     */
    fun session(id: UUID): AdminSession {
        val session = sessionRepository.findById(id) ?: throw AskSessionNotFoundException(id)
        return AdminSession(session, turnRepository.findBySession(id).map { adminTurn(it, session.ipHash) })
    }

    /**
     * Deletes a session and, by cascade, all its turns. The daily totals stay: they hold no
     * personal data.
     *
     * @throws AskSessionNotFoundException if the session doesn't exist (or was already purged)
     */
    fun deleteSession(id: UUID) {
        if (!sessionRepository.deleteById(id)) throw AskSessionNotFoundException(id)
        logger.info("Ask session deleted by an admin: sessionId={}", id)
    }

    /**
     * The daily totals of the days [from]..[to], both inclusive, oldest first. Days without
     * activity have no row. Without [to], the range ends today; without [from], it covers
     * [DEFAULT_STATS_DAYS] days.
     *
     * @throws IllegalArgumentException if [from] is after [to]
     */
    fun dailyStats(from: LocalDate?, to: LocalDate?): List<DailyAskStats> {
        val end = to ?: LocalDate.ofInstant(clock.instant(), HELSINKI_ZONE)
        val start = from ?: end.minusDays(DEFAULT_STATS_DAYS - 1)
        return statsRepository.findByDayRange(start, end)
    }

    private fun adminTurn(turn: AskTurn, ipHash: String?) = AdminTurn(turn, ipHash, guardrailBlockedBy(turn))

    /** The categories that blocked [turn], for a turn the guardrail blocked; empty otherwise. */
    private fun guardrailBlockedBy(turn: AskTurn): List<String> {
        val scores = turn.guardrailScores
        if (turn.guardrailOutcome != GuardrailOutcome.BLOCKED || scores == null) return emptyList()
        return BedrockGuardrailService.blockedBy(guardrailSettings, turn.mode, scores)
    }

    private fun startOf(day: LocalDate): Instant = day.atStartOfDay(HELSINKI_ZONE).toInstant()

    companion object {
        /** How many days [dailyStats] covers when no start day is given. */
        const val DEFAULT_STATS_DAYS = 30L

        private val HELSINKI_ZONE: ZoneId = ZoneId.of("Europe/Helsinki")
    }
}

/**
 * A turn for the admin view.
 *
 * @property ipHash the keyed hash of the session's IP address; null if the session was deleted
 *   while the page was loaded
 * @property guardrailBlockedBy for a blocked turn, the guardrail categories whose stored score
 *   reaches today's threshold; empty for every other turn
 */
data class AdminTurn(
    val turn: AskTurn,
    val ipHash: String?,
    val guardrailBlockedBy: List<String>,
)

/**
 * A session for the admin view.
 *
 * @property turns all turns of the session, oldest first
 */
data class AdminSession(
    val session: AskSession,
    val turns: List<AdminTurn>,
)

/** Thrown when an admin asks for a chat session that doesn't exist. Mapped to HTTP 404. */
class AskSessionNotFoundException(
    val sessionId: UUID,
) : RuntimeException("Chat session not found: $sessionId")