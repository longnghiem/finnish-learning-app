package me.longng.finnish_learning_backend.domain.ask

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** What the recruiter asked for in a turn. */
enum class AskMode {
    /** A chat message about the owner's background. */
    CHAT,

    /** A job description to check against the profile. */
    FIT_CHECK,
}

/** Where a turn stands. Every turn starts as [PENDING] and is completed exactly once. */
enum class TurnStatus {
    /** Stored, still being answered. */
    PENDING,

    /** Answered by the model. */
    ANSWERED,

    /** Refused by the input guardrail. */
    BLOCKED,

    /** Refused because the daily or monthly budget was used up. */
    CAPPED,

    /** Failed (upstream or internal error). */
    ERROR,
}

/** The outcome of the input guardrail check, as stored with a turn. */
enum class GuardrailOutcome {
    PASSED,
    BLOCKED,

    /** The check itself failed; the turn is treated as blocked (fail-closed). */
    ERROR,
}

/**
 * One anonymous chat session, created after the bot check passes.
 *
 * Deleted after 30 days, together with its turns.
 *
 * @property ipHash keyed hash of the client IP, never the IP itself
 */
data class AskSession(
    val id: UUID,
    val ipHash: String,
    val createdAt: Instant,
    val expiresAt: Instant,
) {
    /** True from [expiresAt] on. */
    fun isExpiredAt(now: Instant): Boolean = !now.isBefore(expiresAt)

    companion object {
        /** Length of [ipHash]: an HMAC-SHA256 digest written as lowercase hex. */
        const val IP_HASH_LENGTH = 64
    }
}

/**
 * One tool call the model made while answering, shown to the recruiter as "How I found this".
 *
 * @property args the arguments as the model sent them (parsed JSON)
 * @property isError whether the tool answered with an error result
 */
data class ToolCall(
    val tool: String,
    val args: Map<String, Any?>,
    val isError: Boolean,
)

/** Token counts of all model calls in one turn. Cached tokens are counted separately. */
data class TokenUsage(
    val inputTokens: Int,
    val outputTokens: Int,
    val cacheReadTokens: Int,
    val cacheWriteTokens: Int,
) {
    init {
        require(inputTokens >= 0 && outputTokens >= 0 && cacheReadTokens >= 0 && cacheWriteTokens >= 0) {
            "Token counts must not be negative"
        }
    }

    /** The counts of two sets of model calls added up, e.g. a turn's calls so far plus one more. */
    operator fun plus(other: TokenUsage): TokenUsage = TokenUsage(
        inputTokens = inputTokens + other.inputTokens,
        outputTokens = outputTokens + other.outputTokens,
        cacheReadTokens = cacheReadTokens + other.cacheReadTokens,
        cacheWriteTokens = cacheWriteTokens + other.cacheWriteTokens,
    )

    companion object {
        /** No model call made (yet). */
        val NONE = TokenUsage(inputTokens = 0, outputTokens = 0, cacheReadTokens = 0, cacheWriteTokens = 0)
    }
}

/**
 * One recruiter request and its outcome: a chat message or a fit check.
 *
 * Deleted after 30 days (with its session). The daily totals in [DailyAskStats] are kept.
 *
 * @property userText the message or job description, exactly as received
 * @property answerText the chat answer; null for fit checks and unanswered turns
 * @property fitReportJson the fit-check report as JSON text; null for chat turns
 * @property guardrailOutcome null until the input guardrail has run
 * @property guardrailScores score per guardrail category; null until the guardrail has run
 * @property guardrailTextUnits text units billed by the guardrail check
 * @property costUsd the actual cost of the turn (guardrail plus model)
 */
data class AskTurn(
    val id: Long,
    val sessionId: UUID,
    val mode: AskMode,
    val status: TurnStatus,
    val userText: String,
    val answerText: String?,
    val fitReportJson: String?,
    val toolTrace: List<ToolCall>,
    val guardrailOutcome: GuardrailOutcome?,
    val guardrailScores: Map<String, Double>?,
    val tokens: TokenUsage,
    val guardrailTextUnits: Int,
    val costUsd: BigDecimal,
    val latencyMs: Int?,
    val createdAt: Instant,
)

/**
 * Everything that is known when an [AskTurn] ends. Passed to
 * [me.longng.finnish_learning_backend.persistence.AskTurnRepository.complete].
 */
data class TurnCompletion(
    val status: TurnStatus,
    val answerText: String? = null,
    val fitReportJson: String? = null,
    val toolTrace: List<ToolCall> = emptyList(),
    val guardrailOutcome: GuardrailOutcome? = null,
    val guardrailScores: Map<String, Double>? = null,
    val tokens: TokenUsage = TokenUsage.NONE,
    val guardrailTextUnits: Int = 0,
    val costUsd: BigDecimal = BigDecimal.ZERO,
    val latencyMs: Int? = null,
) {
    init {
        require(status != TurnStatus.PENDING) { "A completed turn can't be PENDING" }
        require(guardrailTextUnits >= 0) { "guardrailTextUnits must not be negative" }
        require(costUsd.signum() >= 0) { "costUsd must not be negative" }
        require(latencyMs == null || latencyMs >= 0) { "latencyMs must not be negative" }
    }
}

/**
 * Optional filters for the admin list of turns. A null property means "don't filter on it".
 *
 * @property from created at or after this time
 * @property to created before this time
 * @property ipHash only turns of sessions with this IP hash
 */
data class TurnFilter(
    val mode: AskMode? = null,
    val status: TurnStatus? = null,
    val from: Instant? = null,
    val to: Instant? = null,
    val ipHash: String? = null,
) {
    init {
        require(from == null || to == null || from.isBefore(to)) { "from must be before to" }
    }
}

/**
 * Anonymous totals for one Europe/Helsinki calendar day. No personal data; kept indefinitely.
 *
 * [spentUsd] and [reservedUsd] back the budget cap: a request first reserves its worst-case cost,
 * then settles it, which moves the reservation to the actual amount spent.
 */
data class DailyAskStats(
    val day: LocalDate,
    val answers: Int,
    val fitChecks: Int,
    val blocked: Int,
    val spentUsd: BigDecimal,
    val reservedUsd: BigDecimal,
)