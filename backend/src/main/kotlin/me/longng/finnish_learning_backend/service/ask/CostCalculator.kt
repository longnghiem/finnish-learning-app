package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.config.AskProperties
import me.longng.finnish_learning_backend.domain.ask.AskMode
import me.longng.finnish_learning_backend.domain.ask.TokenUsage
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/** Decimals of the money columns (`numeric(12, 8)`); finer amounts are rounded by the database. */
const val USD_SCALE = 8

/**
 * Rounds to [USD_SCALE] decimals, the precision the database stores. A reservation and its
 * settlement use rounded amounts, so they match the stored values exactly.
 */
fun BigDecimal.roundToUsdScale(): BigDecimal = setScale(USD_SCALE, RoundingMode.HALF_UP)

/**
 * Prices the chatbot's billable calls with [AskProperties.Price]: the model calls and the input
 * guardrail. Pure arithmetic: nothing here calls AWS or the database.
 *
 * Money is [BigDecimal], never `Double`, and every result is rounded with [roundToUsdScale].
 */
@Component
class CostCalculator(properties: AskProperties) {
    private val price = properties.price
    private val agent = properties.agent
    private val budget = properties.budget

    /**
     * The cost of model calls that used [usage].
     *
     * [TokenUsage.inputTokens] is only the **uncached** part of the input, so the three input
     * counts are added up, each at its own price (a cache read costs 10 % of normal input, a cache
     * write 125 %):
     *
     * `input × input price + output × output price + cache read × cache-read price + cache write × cache-write price`
     */
    fun modelCost(usage: TokenUsage): BigDecimal {
        val total = price.inputPerMillionTokens * usage.inputTokens.toBigDecimal() +
                price.outputPerMillionTokens * usage.outputTokens.toBigDecimal() +
                price.cacheReadPerMillionTokens * usage.cacheReadTokens.toBigDecimal() +
                price.cacheWritePerMillionTokens * usage.cacheWriteTokens.toBigDecimal()
        return total.divide(TOKENS_PER_PRICE_UNIT).roundToUsdScale()
    }

    /**
     * The cost of one guardrail call. Its two checks are billed separately, at different prices,
     * as [GuardrailCheckResult.contentFilterTextUnits] and [GuardrailCheckResult.promptAttackTextUnits].
     */
    fun guardrailCost(contentFilterTextUnits: Int, promptAttackTextUnits: Int): BigDecimal {
        require(contentFilterTextUnits >= 0 && promptAttackTextUnits >= 0) { "Text units must not be negative" }
        val total = price.guardrailContentPerKUnits * contentFilterTextUnits.toBigDecimal() +
                price.guardrailPromptAttackPerKUnits * promptAttackTextUnits.toBigDecimal()
        return total.divide(TEXT_UNITS_PER_PRICE_UNIT).roundToUsdScale()
    }

    /**
     * The most one request can cost: what [BudgetService.reserve] sets aside before the request's
     * first billable call. Deliberately pessimistic; [BudgetService.settle] gives the surplus back.
     *
     * Every model call is assumed to use its full output limit.
     * Reference values at the default settings, with a ≈ 2,300-token prompt (6,900 characters):
     *
     * | Request | Estimate | Typical actual |
     * |---|---|---|
     * | Chat, first 500-character message | ≈ $0.046 | ≈ $0.011 |
     * | Chat, 10th message (≈ 5,000 tokens of history) | ≈ $0.068 | ≈ $0.02 |
     * | Fit check, 6,000-character job description | ≈ $0.145 | ≈ $0.03 |
     *
     * @param promptChars the system prompt, the profile index and the tool specifications
     * @param inputChars the recruiter's message or job description
     * @param historyChars the earlier messages and answers of the conversation; 0 for a fit check
     */
    fun estimateMax(mode: AskMode, promptChars: Int, inputChars: Int, historyChars: Int): BigDecimal {
        require(promptChars >= 0 && inputChars >= 0 && historyChars >= 0) { "Character counts must not be negative" }
        val outputLimits = outputLimitsPerCall(mode)
        val textTokens = Math.ceilDiv(promptChars + inputChars + historyChars, CHARS_PER_TOKEN)
        val toolResultTokensPerRound = agent.maxReadsPerRound * budget.toolResultTokens
        val inputTokens = outputLimits.indices.sumOf { earlierRounds ->
            textTokens + earlierRounds * toolResultTokensPerRound
        }
        val worstCase = TokenUsage(
            inputTokens = inputTokens,
            outputTokens = outputLimits.sum(),
            cacheReadTokens = 0,
            cacheWriteTokens = 0,
        )
        val guardrailTextUnits = GuardrailCheckResult.billedTextUnits(inputChars)
        return modelCost(worstCase) + guardrailCost(guardrailTextUnits, guardrailTextUnits)
    }

    /** The output limit of each model call one request of [mode] may make, in call order. */
    private fun outputLimitsPerCall(mode: AskMode): List<Int> = when (mode) {
        AskMode.CHAT -> List(agent.chatMaxToolRounds + 1) { agent.chatMaxTokens }
        AskMode.FIT_CHECK -> List(agent.fitMaxToolRounds) { agent.fitStepMaxTokens } + agent.fitReportMaxTokens
    }

    private companion object {
        val TOKENS_PER_PRICE_UNIT = BigDecimal(1_000_000)
        val TEXT_UNITS_PER_PRICE_UNIT = BigDecimal(1_000)
        const val CHARS_PER_TOKEN = 3
    }
}