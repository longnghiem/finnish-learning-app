package me.longng.finnish_learning_backend.controller.dto.ask

import com.fasterxml.jackson.annotation.JsonRawValue
import me.longng.finnish_learning_backend.controller.AdminAskController
import me.longng.finnish_learning_backend.domain.ask.AskMode
import me.longng.finnish_learning_backend.domain.ask.DailyAskStats
import me.longng.finnish_learning_backend.domain.ask.GuardrailOutcome
import me.longng.finnish_learning_backend.domain.ask.TokenUsage
import me.longng.finnish_learning_backend.domain.ask.TurnStatus
import me.longng.finnish_learning_backend.service.ask.AdminSession
import me.longng.finnish_learning_backend.service.ask.AdminTurn
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** How many characters of an IP hash the admin view shows: enough to group sessions, no more. */
private const val IP_HASH_PREFIX_LENGTH = 8

/**
 * One stored turn with everything that was recorded about it, as the admin API
 * ([AdminAskController]) returns it. The frontend's `frontend/src/schemas/adminAsk.ts` must match
 * the classes in this file.
 *
 * @property ipHashPrefix the first characters of the session's IP hash, to see which sessions
 *   came from the same visitor; null if the session was deleted while the page was loaded
 * @property fitReport the fit report as a JSON object (not as a string); null for chat turns
 * @property guardrailScores the input guardrail's score per category, 0.0 to 1.0
 * @property guardrailBlockedBy for a blocked turn, the categories whose score reaches its
 *   threshold (today's thresholds); empty for every other turn
 * @property costUsd what the turn cost: the guardrail plus every model call
 */
data class AdminTurnResponse(
    val id: Long,
    val sessionId: UUID,
    val ipHashPrefix: String?,
    val mode: AskMode,
    val status: TurnStatus,
    val userText: String,
    val answerText: String?,
    @field:JsonRawValue
    val fitReport: String?,
    val toolTrace: List<AskToolCall>,
    val guardrailOutcome: GuardrailOutcome?,
    val guardrailScores: Map<String, Double>?,
    val guardrailBlockedBy: List<String>,
    val tokens: TokenUsage,
    val guardrailTextUnits: Int,
    val costUsd: BigDecimal,
    val latencyMs: Int?,
    val createdAt: Instant,
) {
    companion object {
        fun from(adminTurn: AdminTurn): AdminTurnResponse {
            val turn = adminTurn.turn
            return AdminTurnResponse(
                id = turn.id,
                sessionId = turn.sessionId,
                ipHashPrefix = adminTurn.ipHash?.take(IP_HASH_PREFIX_LENGTH),
                mode = turn.mode,
                status = turn.status,
                userText = turn.userText,
                answerText = turn.answerText,
                fitReport = turn.fitReportJson,
                toolTrace = turn.toolTrace.map { AskToolCall.from(it) },
                guardrailOutcome = turn.guardrailOutcome,
                guardrailScores = turn.guardrailScores,
                guardrailBlockedBy = adminTurn.guardrailBlockedBy,
                tokens = turn.tokens,
                guardrailTextUnits = turn.guardrailTextUnits,
                costUsd = turn.costUsd,
                latencyMs = turn.latencyMs,
                createdAt = turn.createdAt,
            )
        }
    }
}

/**
 * One page of turns, newest first. A page with fewer than [limit] turns is the last one.
 *
 * @property offset how many newer turns came before this page
 */
data class AdminTurnPageResponse(
    val turns: List<AdminTurnResponse>,
    val limit: Int,
    val offset: Int,
)

/**
 * One chat session with all its turns.
 *
 * @property ipHashPrefix the first characters of the session's IP hash
 * @property turns oldest first, like the conversation went
 */
data class AdminSessionResponse(
    val id: UUID,
    val ipHashPrefix: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val turns: List<AdminTurnResponse>,
) {
    companion object {
        fun from(detail: AdminSession) = AdminSessionResponse(
            id = detail.session.id,
            ipHashPrefix = detail.session.ipHash.take(IP_HASH_PREFIX_LENGTH),
            createdAt = detail.session.createdAt,
            expiresAt = detail.session.expiresAt,
            turns = detail.turns.map { AdminTurnResponse.from(it) },
        )
    }
}

/**
 * The totals of one Europe/Helsinki calendar day.
 *
 * @property reservedUsd money set aside by requests that were still running; normally 0
 */
data class AdminDailyStatsResponse(
    val day: LocalDate,
    val answers: Int,
    val fitChecks: Int,
    val blocked: Int,
    val spentUsd: BigDecimal,
    val reservedUsd: BigDecimal,
) {
    companion object {
        fun from(stats: DailyAskStats) = AdminDailyStatsResponse(
            day = stats.day,
            answers = stats.answers,
            fitChecks = stats.fitChecks,
            blocked = stats.blocked,
            spentUsd = stats.spentUsd,
            reservedUsd = stats.reservedUsd,
        )
    }
}