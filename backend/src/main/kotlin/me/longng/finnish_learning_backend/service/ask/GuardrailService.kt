package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.domain.ask.AskMode
import me.longng.finnish_learning_backend.domain.ask.GuardrailOutcome

/**
 * Screens recruiter input (a chat message or a job description) **before** any model call, so
 * an attack is stopped before it costs model tokens.
 *
 * Only inputs are checked, never the model's answers. That is an accepted risk (ADR 0002): the
 * model can only see the curated profile, and the red-team tests check its answers to attacks.
 */
interface GuardrailService {

    /**
     * Checks [text] for harmful content and prompt attacks, with the thresholds of [mode].
     *
     * Fail-closed: when the check can't be made, the input must be treated as blocked.
     *
     * @throws IllegalArgumentException if [text] is blank
     * @throws GuardrailUnavailableException if the check couldn't be made
     */
    fun check(mode: AskMode, text: String): GuardrailCheckResult
}

/** Whether an input may go to the model. */
sealed interface GuardrailVerdict {

    /** No score reached its threshold. */
    data object Passed : GuardrailVerdict

    /**
     * At least one score reached its threshold.
     *
     * @property categories the categories that reached it, e.g. `JAILBREAK` or `HATE`
     */
    data class Blocked(val categories: List<String>) : GuardrailVerdict {
        init {
            require(categories.isNotEmpty()) { "A blocked verdict needs at least one category" }
        }
    }
}

/**
 * The result of one [GuardrailService.check].
 *
 * @property scores the score of every checked category (0.0 to 1.0), for the admin view
 * @property contentFilterTextUnits text units billed for the content filter
 * @property promptAttackTextUnits text units billed for the prompt-attack check. The two checks are
 *   billed separately, at different prices.
 */
data class GuardrailCheckResult(
    val verdict: GuardrailVerdict,
    val scores: Map<String, Double>,
    val contentFilterTextUnits: Int,
    val promptAttackTextUnits: Int,
) {
    /** True if the input must not go to the model. */
    val isBlocked: Boolean get() = verdict is GuardrailVerdict.Blocked

    /** All billed text units, as stored with the turn. */
    val textUnits: Int get() = contentFilterTextUnits + promptAttackTextUnits

    /** The verdict as stored with the turn. */
    val outcome: GuardrailOutcome
        get() = when (verdict) {
            GuardrailVerdict.Passed -> GuardrailOutcome.PASSED
            is GuardrailVerdict.Blocked -> GuardrailOutcome.BLOCKED
        }
}

/**
 * Thrown when the guardrail check couldn't be made: Bedrock unreachable, too slow, throttling,
 * missing permission, or an incomplete answer. The input is then treated as blocked
 * (fail-closed), and the turn is stored with [GuardrailOutcome.ERROR].
 */
class GuardrailUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)