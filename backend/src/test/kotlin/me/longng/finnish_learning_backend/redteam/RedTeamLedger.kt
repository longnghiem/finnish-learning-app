package me.longng.finnish_learning_backend.redteam

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Collections
import kotlin.test.assertTrue

/**
 * The outcome of one red-team case, for the summary table.
 *
 * @property outcome what happened, e.g. `BLOCKED` or `ANSWERED`
 * @property maxScore the highest input-guardrail score of any category, 0.0 to 1.0; null if the
 *   guardrail didn't run
 * @property blockedBy the guardrail categories at or above their threshold
 * @property details anything else worth reading, e.g. a fit report's verdict counts
 * @property problems the failed checks; empty if the case passed
 */
data class CaseResult(
    val id: String,
    val outcome: String,
    val costUsd: BigDecimal,
    val maxScore: Double?,
    val blockedBy: List<String>,
    val details: String,
    val problems: List<String>,
)

/**
 * Collects the [CaseResult]s of one red-team class and prints them as a table at the end, so one
 * run shows every case: passed or failed, how close each input came to the guardrail's threshold,
 * and what it cost.
 *
 * @param costLimitUsd the most the class may spend. More means something runs away (a loop, a
 *   much longer answer than expected), so the class fails, after the table is printed
 */
class RedTeamLedger(private val title: String, private val costLimitUsd: BigDecimal) {

    // JUnit may run the cases on other threads than the one that prints the summary.
    private val results: MutableList<CaseResult> = Collections.synchronizedList(mutableListOf())

    fun record(result: CaseResult) {
        results += result
    }

    val totalCostUsd: BigDecimal
        get() = synchronized(results) { results.fold(BigDecimal.ZERO) { sum, it -> sum + it.costUsd } }

    /** The summary table, one line per case, then the totals. */
    fun summary(): String = synchronized(results) {
        val lines = results.sortedBy { it.id }.map { result ->
            listOf(
                result.id.padEnd(8),
                (if (result.problems.isEmpty()) "pass" else "FAIL").padEnd(5),
                result.outcome.padEnd(9),
                (result.maxScore?.toString() ?: "-").padEnd(5),
                result.blockedBy.joinToString(",").ifEmpty { "-" }.padEnd(26),
                "$" + result.costUsd.setScale(4, RoundingMode.HALF_UP).toPlainString(),
                "  " + result.details,
            ).joinToString(" ") + result.problems.joinToString("") { "\n           ! $it" }
        }
        val failed = results.count { it.problems.isNotEmpty() }
        val header = "id       check outcome   score blockedBy                  cost"
        val totals = "${results.size} cases, $failed failed, total cost $" +
                totalCostUsd.setScale(4, RoundingMode.HALF_UP).toPlainString() + " (limit $" + costLimitUsd.toPlainString() + ")"
        "\n=== Red team: $title ===\n$header\n${lines.joinToString("\n")}\n$totals\n"
    }

    /** Fails if the class spent more than its limit. */
    fun assertWithinCostLimit() {
        assertTrue(totalCostUsd <= costLimitUsd, "Red team $title cost $${totalCostUsd.toPlainString()}, above the limit of $$costLimitUsd")
    }
}