package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.domain.ask.AskMode
import me.longng.finnish_learning_backend.domain.ask.GuardrailOutcome
import me.longng.finnish_learning_backend.domain.ask.TokenUsage
import me.longng.finnish_learning_backend.domain.ask.ToolCall
import me.longng.finnish_learning_backend.domain.ask.TurnCompletion
import me.longng.finnish_learning_backend.domain.ask.TurnStatus
import me.longng.finnish_learning_backend.persistence.AskDailyStatsRepository
import me.longng.finnish_learning_backend.persistence.AskTurnRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * The bookkeeping every recruiter request shares, chat message ([ChatService]) or fit check
 * ([FitCheckService]): the stored turn, the budget reservation and the daily counters.
 *
 * The rules it keeps:
 * - every turn is stored as PENDING first ([start]) and completed exactly once, as ANSWERED,
 *   BLOCKED, CAPPED or ERROR;
 * - the reservation is settled exactly once with what was really spent (the guardrail plus every
 *   model call), or released if nothing was spent, also when the request fails halfway;
 * - a failure while recording never hides the failure that caused it.
 *
 * Logs carry the turn id, the status, the cost and the latency, never the visitor's text.
 */
@Component
class AskTurnRecorder(
    private val turnRepository: AskTurnRepository,
    private val statsRepository: AskDailyStatsRepository,
    private val budgetService: BudgetService,
    private val costCalculator: CostCalculator,
) {
    private val logger = LoggerFactory.getLogger(AskTurnRecorder::class.java)

    /** Stores a new PENDING turn for [userText] and starts its bookkeeping. */
    fun start(sessionId: UUID, mode: AskMode, userText: String): TurnInProgress =
        TurnInProgress(turnRepository.insertPending(sessionId, mode, userText), mode)

    /**
     * Reserves [estimateUsd], the most the turn can cost, before its first paid call.
     *
     * @throws AskBudgetExhaustedException if a cap has no room for it
     */
    fun reserve(turn: TurnInProgress, estimateUsd: BigDecimal): Reservation =
        budgetService.reserve(turn.mode, estimateUsd).also { turn.reservation = it }

    /**
     * Stores how the turn ended and settles its reservation with the actual cost.
     *
     * @param answerText the chat answer, for an answered chat turn
     * @param fitReportJson the report as JSON, for an answered fit check
     */
    fun finish(turn: TurnInProgress, status: TurnStatus, answerText: String? = null, fitReportJson: String? = null) {
        turnRepository.complete(turn.id, completionOf(turn, status, answerText, fitReportJson, turn.guardrail?.outcome))
        turn.completed = true
        settle(turn)
    }

    /**
     * Adds the turn to the daily counters. The turn is already stored and its cost settled, so a
     * failure here loses only a statistic: it is logged, and the visitor still gets the outcome.
     */
    fun countOutcome(turn: TurnInProgress, day: LocalDate, answers: Int = 0, fitChecks: Int = 0, blocked: Int = 0) {
        try {
            statsRepository.incrementCounters(day, answers = answers, fitChecks = fitChecks, blocked = blocked)
        } catch (ex: Exception) {
            logger.warn("Daily counters not updated: turnId={} error={}", turn.id, ex.javaClass.simpleName)
        }
    }

    /**
     * Records a turn that failed: CAPPED if the budget was full, ERROR otherwise, with what was
     * spent until then. Problems while recording are attached to [ex], never hide it.
     */
    fun recordFailure(turn: TurnInProgress, ex: Exception) {
        val status = if (ex is AskBudgetExhaustedException) TurnStatus.CAPPED else TurnStatus.ERROR
        val guardrailOutcome = if (ex is GuardrailUnavailableException) GuardrailOutcome.ERROR else turn.guardrail?.outcome
        if (!turn.completed) {
            try {
                turnRepository.complete(turn.id, completionOf(turn, status, answerText = null, fitReportJson = null, guardrailOutcome))
                turn.completed = true
            } catch (inner: Exception) {
                ex.addSuppressed(inner)
            }
        }
        try {
            settle(turn)
        } catch (inner: Exception) {
            ex.addSuppressed(inner)
        }
        logger.warn(
            "{} turn failed: turnId={} status={} error={} costUsd={} latencyMs={}",
            turn.label,
            turn.id,
            status,
            ex.javaClass.simpleName,
            costOf(turn),
            turn.latencyMs(),
        )
    }

    /**
     * The guardrail check plus all model calls so far. A guardrail check that failed is counted as
     * $0: its price (≈ $0.00015) is too small to matter.
     */
    fun costOf(turn: TurnInProgress): BigDecimal {
        val guardrailCost = turn.guardrail?.let {
            costCalculator.guardrailCost(it.contentFilterTextUnits, it.promptAttackTextUnits)
        } ?: BigDecimal.ZERO
        return guardrailCost + costCalculator.modelCost(turn.tokens)
    }

    /** Settles the reservation, if any, exactly once: released if nothing was spent. */
    private fun settle(turn: TurnInProgress) {
        val reservation = turn.reservation ?: return
        turn.reservation = null
        val cost = costOf(turn)
        if (cost.signum() == 0) budgetService.release(reservation) else budgetService.settle(reservation, cost)
    }

    private fun completionOf(
        turn: TurnInProgress,
        status: TurnStatus,
        answerText: String?,
        fitReportJson: String?,
        guardrailOutcome: GuardrailOutcome?,
    ) = TurnCompletion(
        status = status,
        answerText = answerText,
        fitReportJson = fitReportJson,
        toolTrace = turn.toolTrace,
        guardrailOutcome = guardrailOutcome,
        guardrailScores = turn.guardrail?.scores,
        tokens = turn.tokens,
        guardrailTextUnits = turn.guardrail?.textUnits ?: 0,
        costUsd = costOf(turn),
        latencyMs = turn.latencyMs(),
    )
}

/**
 * What one turn has done so far, so a failure can still be recorded with what was spent. Created
 * by [AskTurnRecorder.start]; the service that runs the turn fills it in as it goes.
 *
 * @property rounds the tool rounds so far: one round is one model reply asking for tools, and
 *   running them
 */
class TurnInProgress internal constructor(val id: Long, val mode: AskMode) {
    private val startedAt = System.nanoTime()
    internal var reservation: Reservation? = null
    internal var completed = false
    var guardrail: GuardrailCheckResult? = null
    var tokens: TokenUsage = TokenUsage.NONE
    var toolTrace: List<ToolCall> = emptyList()
    var rounds = 0

    /** "Chat" or "Fit check", for log lines. */
    val label: String
        get() = when (mode) {
            AskMode.CHAT -> "Chat"
            AskMode.FIT_CHECK -> "Fit check"
        }

    fun latencyMs(): Int = ((System.nanoTime() - startedAt) / 1_000_000).toInt()
}