package me.longng.finnish_learning_backend.controller.dto.ask

import me.longng.finnish_learning_backend.service.ask.AskStats

/**
 * Response of `GET /api/ask/stats`: public totals for the "How this works" panel.
 *
 * @property answersTotal chat answers plus fit checks, since the chatbot started
 * @property attacksBlockedTotal messages the input guardrail refused, since the chatbot started
 * @property budgetUsedPercentThisMonth how much of this month's budget is used, 0 to 100
 */
data class AskStatsResponse(
    val answersTotal: Long,
    val attacksBlockedTotal: Long,
    val budgetUsedPercentThisMonth: Int,
) {
    companion object {
        fun from(stats: AskStats) = AskStatsResponse(
            answersTotal = stats.answersTotal,
            attacksBlockedTotal = stats.blockedTotal,
            budgetUsedPercentThisMonth = stats.budgetUsedPercentThisMonth,
        )
    }
}