package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.domain.ask.AskMode
import me.longng.finnish_learning_backend.domain.ask.AskTurn
import me.longng.finnish_learning_backend.domain.ask.DailyAskStats
import me.longng.finnish_learning_backend.domain.ask.TokenUsage
import me.longng.finnish_learning_backend.domain.ask.TurnCompletion
import me.longng.finnish_learning_backend.domain.ask.TurnFilter
import me.longng.finnish_learning_backend.domain.ask.TurnStatus
import me.longng.finnish_learning_backend.persistence.AskDailyStatsRepository
import me.longng.finnish_learning_backend.persistence.AskTurnRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * An [AskTurnRepository] in memory, for service tests. Behaves like the real one where the
 * services rely on it: ids count up, [complete] works once per turn, [findBySession] is oldest first.
 */
class InMemoryAskTurnRepository : AskTurnRepository {
    private val turns = linkedMapOf<Long, AskTurn>()
    private var nextId = 1L

    /** All stored turns, oldest first. */
    val all: List<AskTurn> get() = turns.values.toList()

    /** Stores a finished turn directly, e.g. an earlier answer of the conversation. */
    fun add(sessionId: UUID, mode: AskMode, status: TurnStatus, userText: String, answerText: String? = null): AskTurn {
        val id = insertPending(sessionId, mode, userText)
        complete(id, TurnCompletion(status = status, answerText = answerText))
        return turns.getValue(id)
    }

    override fun insertPending(sessionId: UUID, mode: AskMode, userText: String): Long {
        val id = nextId++
        turns[id] = AskTurn(
            id = id,
            sessionId = sessionId,
            mode = mode,
            status = TurnStatus.PENDING,
            userText = userText,
            answerText = null,
            fitReportJson = null,
            toolTrace = emptyList(),
            guardrailOutcome = null,
            guardrailScores = null,
            tokens = TokenUsage.NONE,
            guardrailTextUnits = 0,
            costUsd = BigDecimal.ZERO,
            latencyMs = null,
            createdAt = Instant.parse("2098-10-15T12:00:00Z"),
        )
        return id
    }

    override fun complete(id: Long, completion: TurnCompletion) {
        val turn = checkNotNull(turns[id]) { "No turn $id" }
        check(turn.status == TurnStatus.PENDING) { "Turn $id is already ${turn.status}" }
        turns[id] = turn.copy(
            status = completion.status,
            answerText = completion.answerText,
            fitReportJson = completion.fitReportJson,
            toolTrace = completion.toolTrace,
            guardrailOutcome = completion.guardrailOutcome,
            guardrailScores = completion.guardrailScores,
            tokens = completion.tokens,
            guardrailTextUnits = completion.guardrailTextUnits,
            costUsd = completion.costUsd,
            latencyMs = completion.latencyMs,
        )
    }

    override fun findBySession(sessionId: UUID): List<AskTurn> = turns.values.filter { it.sessionId == sessionId }

    override fun countTurns(sessionId: UUID, mode: AskMode): Int =
        turns.values.count { it.sessionId == sessionId && it.mode == mode }

    override fun countForIpSince(ipHash: String, mode: AskMode, since: Instant): Int =
        throw UnsupportedOperationException("Not needed by the service tests")

    override fun findPage(filter: TurnFilter, limit: Int, offset: Int): List<AskTurn> =
        throw UnsupportedOperationException("Not needed by the service tests")
}

/**
 * An [AskDailyStatsRepository] in memory, for service tests. The budget lock does nothing: the
 * tests run on one thread. [reservations] records every amount set aside.
 */
class InMemoryAskDailyStatsRepository : AskDailyStatsRepository {
    private val days = mutableMapOf<LocalDate, DailyAskStats>()

    /** Every [addReservation] amount, in order. */
    val reservations = mutableListOf<BigDecimal>()

    override fun acquireBudgetLock() = Unit

    override fun addReservation(day: LocalDate, amountUsd: BigDecimal) {
        reservations += amountUsd
        update(day) { it.copy(reservedUsd = it.reservedUsd + amountUsd) }
    }

    override fun settleReservation(day: LocalDate, reservedUsd: BigDecimal, actualUsd: BigDecimal) {
        val stats = checkNotNull(days[day]) { "Nothing reserved on $day" }
        check(stats.reservedUsd >= reservedUsd) { "Settling more than was reserved" }
        days[day] = stats.copy(reservedUsd = stats.reservedUsd - reservedUsd, spentUsd = stats.spentUsd + actualUsd)
    }

    override fun incrementCounters(day: LocalDate, answers: Int, fitChecks: Int, blocked: Int) =
        update(day) {
            it.copy(answers = it.answers + answers, fitChecks = it.fitChecks + fitChecks, blocked = it.blocked + blocked)
        }

    override fun findByDay(day: LocalDate): DailyAskStats? = days[day]

    override fun sumSpentAndReservedUsd(from: LocalDate, to: LocalDate): BigDecimal =
        days.values.filter { it.day in from..to }.fold(BigDecimal.ZERO) { sum, it -> sum + it.spentUsd + it.reservedUsd }

    override fun findByDayRange(from: LocalDate, to: LocalDate): List<DailyAskStats> =
        days.values.filter { it.day in from..to }.sortedBy { it.day }

    private fun update(day: LocalDate, change: (DailyAskStats) -> DailyAskStats) {
        val stats = days[day] ?: DailyAskStats(day, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO)
        days[day] = change(stats)
    }
}