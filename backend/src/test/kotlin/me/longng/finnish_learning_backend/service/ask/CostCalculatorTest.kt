package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.config.AskProperties
import me.longng.finnish_learning_backend.config.testAskProperties
import me.longng.finnish_learning_backend.domain.ask.AskMode
import me.longng.finnish_learning_backend.domain.ask.TokenUsage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import kotlin.test.assertEquals

class CostCalculatorTest {

    companion object {
        /** A system prompt, profile index and tool specs of ≈ 2,300 tokens. */
        private const val PROMPT_CHARS = 6900

        /**
         * Every price at zero. Tests set one price to 1 USD per unit, so the cost is the number of
         * tokens (or text units) of that one kind, in millionths (or thousandths) of a dollar.
         */
        private val ZERO_PRICES = AskProperties.Price(
            inputPerMillionTokens = BigDecimal.ZERO,
            outputPerMillionTokens = BigDecimal.ZERO,
            cacheReadPerMillionTokens = BigDecimal.ZERO,
            cacheWritePerMillionTokens = BigDecimal.ZERO,
            guardrailContentPerKUnits = BigDecimal.ZERO,
            guardrailPromptAttackPerKUnits = BigDecimal.ZERO,
        )
    }

    private val calculator = CostCalculator(testAskProperties())

    private fun calculatorWith(price: AskProperties.Price) = CostCalculator(testAskProperties(price = price))

    @Test
    fun testModelCost_AllUsageFields() {
        val usage = TokenUsage(inputTokens = 1000, outputTokens = 100, cacheReadTokens = 2000, cacheWriteTokens = 300)

        // 1000 × 1.10 + 100 × 5.50 + 2000 × 0.11 + 300 × 1.375 = 2282.5 per million
        assertEquals(BigDecimal("0.00228250"), calculator.modelCost(usage))
    }

    @Test
    fun testModelCost_CachedUsageFromRealCall() {
        // A real cached call: only 3 input tokens were uncached. Counting inputTokens alone as the
        // whole input would make this call look ≈ 99 % cheaper than it was.
        val usage = TokenUsage(inputTokens = 3, outputTokens = 26, cacheReadTokens = 5698, cacheWriteTokens = 77)

        // Exactly 0.000878955, rounded to the 8 decimals the database stores.
        assertEquals(BigDecimal("0.00087896"), calculator.modelCost(usage))
    }

    @Test
    fun testModelCost_NoUsageIsFree() {
        assertEquals(0, calculator.modelCost(TokenUsage.NONE).signum())
    }

    @Test
    fun testModelCost_RoundsToStoredScale() {
        // One cache-write token costs 0.000001375: one decimal more than the database keeps.
        val cost = calculator.modelCost(TokenUsage(inputTokens = 0, outputTokens = 0, cacheReadTokens = 0, cacheWriteTokens = 1))

        assertEquals(BigDecimal("0.00000138"), cost)
        assertEquals(USD_SCALE, cost.scale())
    }

    @Test
    fun testGuardrailCost_PricesChecksSeparately() {
        assertEquals(BigDecimal("0.00015000"), calculator.guardrailCost(contentFilterTextUnits = 1, promptAttackTextUnits = 1))
        assertEquals(BigDecimal("0.00014000"), calculator.guardrailCost(contentFilterTextUnits = 2, promptAttackTextUnits = 0))
        assertEquals(BigDecimal("0.00016000"), calculator.guardrailCost(contentFilterTextUnits = 0, promptAttackTextUnits = 2))
    }

    @Test
    fun testEstimateMax_ChatFirstMessageReferenceValue() {
        // 4 calls: input 4 × 2,467 + (0 + 1 + 2 + 3) × 4,000 = 33,868 tokens; output 4 × 400 = 1,600;
        // guardrail 1 unit per check
        val estimate = calculator.estimateMax(AskMode.CHAT, promptChars = PROMPT_CHARS, inputChars = 500, historyChars = 0)

        assertEquals(BigDecimal("0.04620480"), estimate)
    }

    @Test
    fun testEstimateMax_FitCheckReferenceValue() {
        // 7 calls: input 7 × 4,300 + (0 + 1 + … + 6) × 4,000 = 114,100 tokens; output 6 × 300 + 1,500
        // = 3,300; guardrail 6 units per check
        val estimate = calculator.estimateMax(AskMode.FIT_CHECK, promptChars = PROMPT_CHARS, inputChars = 6000, historyChars = 0)

        assertEquals(BigDecimal("0.14456000"), estimate)
    }

    @Test
    fun testEstimateMax_ChatGivesEveryCallTheChatLimit() {
        val outputOnly = calculatorWith(ZERO_PRICES.copy(outputPerMillionTokens = BigDecimal.ONE))

        // 3 tool rounds + 1 answer call, 400 tokens each
        assertEquals(
            BigDecimal("0.00160000"),
            outputOnly.estimateMax(AskMode.CHAT, promptChars = PROMPT_CHARS, inputChars = 500, historyChars = 0),
        )
    }

    @Test
    fun testEstimateMax_FitUsesStepLimitForResearchCalls() {
        val outputOnly = calculatorWith(ZERO_PRICES.copy(outputPerMillionTokens = BigDecimal.ONE))

        // 6 research calls × 300 + 1 report call × 1,500 = 3,300 tokens, not 7 × 1,500
        assertEquals(
            BigDecimal("0.00330000"),
            outputOnly.estimateMax(AskMode.FIT_CHECK, promptChars = PROMPT_CHARS, inputChars = 6000, historyChars = 0),
        )
    }

    @Test
    fun testEstimateMax_AddsToolResultAllowancePerEarlierCall() {
        val inputOnly = calculatorWith(ZERO_PRICES.copy(inputPerMillionTokens = BigDecimal.ONE))

        // No text at all: only the tool results. Call k carries k − 1 rounds of 4 reads × 1,000
        // tokens, so the 4 chat calls carry (0 + 1 + 2 + 3) × 4,000 = 24,000 tokens.
        assertEquals(
            BigDecimal("0.02400000"),
            inputOnly.estimateMax(AskMode.CHAT, promptChars = 0, inputChars = 0, historyChars = 0),
        )
    }

    @Test
    fun testEstimateMax_TextCountsInEveryCall() {
        val inputOnly = calculatorWith(ZERO_PRICES.copy(inputPerMillionTokens = BigDecimal.ONE))

        // 5 + 3 + 2 = 10 characters → 4 tokens (10 / 3, rounded up), in each of the 4 calls,
        // on top of the 24,000 tool-result tokens
        assertEquals(
            BigDecimal("0.02401600"),
            inputOnly.estimateMax(AskMode.CHAT, promptChars = 5, inputChars = 3, historyChars = 2),
        )
    }

    @Test
    fun testEstimateMax_GuardrailChecksOnlyTheInput() {
        val guardrailOnly = calculatorWith(
            ZERO_PRICES.copy(guardrailContentPerKUnits = BigDecimal.ONE, guardrailPromptAttackPerKUnits = BigDecimal.ONE),
        )

        // 1,001 characters → 2 units per check, 4 in all. The prompt isn't checked by the guardrail.
        assertEquals(
            BigDecimal("0.00400000"),
            guardrailOnly.estimateMax(AskMode.CHAT, promptChars = PROMPT_CHARS, inputChars = 1001, historyChars = 0),
        )
    }

    @Test
    fun testEstimateMax_NegativeCharsRejected() {
        assertThrows<IllegalArgumentException> {
            calculator.estimateMax(AskMode.CHAT, promptChars = PROMPT_CHARS, inputChars = -1, historyChars = 0)
        }
    }
}