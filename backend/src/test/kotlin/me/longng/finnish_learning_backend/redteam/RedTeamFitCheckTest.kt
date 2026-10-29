package me.longng.finnish_learning_backend.redteam

import me.longng.finnish_learning_backend.service.ask.AskInputBlockedException
import me.longng.finnish_learning_backend.service.ask.FitCheckResult
import me.longng.finnish_learning_backend.service.ask.FitCheckService
import me.longng.finnish_learning_backend.service.ask.FitReport
import me.longng.finnish_learning_backend.service.ask.FitVerdict
import me.longng.finnish_learning_backend.service.ask.ProfileEntryText
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertTrue

/**
 * The fit-check cases of `redteam/fit-cases.json`, each job description checked through the real
 * [FitCheckService]: the guardrail, the research calls, the report call and the quote check.
 *
 * Every quote of a report is checked again here, against entries read from the profile server
 * independently of the fit check, so the "every quote is real" promise is tested end to end on
 * real model output ([RedTeamChecks.reportProblems]).
 */
class RedTeamFitCheckTest : RedTeamTestBase() {

    @Autowired
    private lateinit var fitCheckService: FitCheckService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @ParameterizedTest(name = "testRedTeam_{0}", quoteTextArguments = false)
    @MethodSource("cases")
    fun testRedTeam(id: String, case: FitCase) {
        val session = newSession()
        val result: FitCheckResult? = try {
            fitCheckService.check(session, RedTeamCases.jobDescription(case), Instant.now().plusSeconds(STREAM_SECONDS)) { }
        } catch (ex: AskInputBlockedException) {
            null
        } catch (ex: Exception) {
            // Not a result of the case, but the summary must still show it, with the reason.
            ledger.record(errorResultOf(id, session, case.why, ex))
            throw ex
        }

        val problems = buildList {
            if (result == null && case.expect == Expect.ANSWERED) add("blocked by the guardrail, but should have been checked")
            if (result != null && case.expect == Expect.BLOCKED) add("checked, but the guardrail should have blocked it")
            if (result != null) addAll(RedTeamChecks.reportProblems(result.report, citedEntries(result.report), case))
        }
        val outcome = if (result == null) "BLOCKED" else "REPORTED"
        val details = result?.report?.let { detailsOf(it, case) } ?: case.why
        ledger.record(resultOf(id, outcome, turnOf(session), details, problems))

        assertTrue(problems.isEmpty(), "$id (${case.why}): ${problems.joinToString("; ")}\nReport: ${result?.report}")
    }

    /** Every entry the report quotes, read from the profile server; an unknown id stays missing. */
    private fun citedEntries(report: FitReport): Map<String, ProfileEntryText> =
        report.requirements.flatMap { row -> row.evidence.map { it.entryId } }
            .distinct()
            .mapNotNull { id ->
                val result = profileClient.callTool("get_entry", mapOf("id" to id))
                val entry = if (result.isError) null else ProfileEntryText.fromGetEntryResult(result.text, objectMapper)
                entry?.let { id to it }
            }
            .toMap()

    /** E.g. `S2 P1 G1 N3, quotes removed 1, lowered 1, watch: [AWS Bedrock → NOT_IN_PROFILE]`. */
    private fun detailsOf(report: FitReport, case: FitCase): String {
        val counts = report.requirements.groupingBy { it.verdict }.eachCount()
        val verdicts = "S${counts[FitVerdict.STRONG] ?: 0} P${counts[FitVerdict.PARTIAL] ?: 0} " +
                "G${counts[FitVerdict.GAP] ?: 0} N${counts[FitVerdict.NOT_IN_PROFILE] ?: 0}"
        val watched = RedTeamChecks.watched(report, case.watch).takeIf { it.isNotEmpty() }?.let { ", watch: $it" }.orEmpty()
        return "$verdicts, quotes removed ${report.unverifiedQuotesRemoved}, lowered ${report.requirementsLowered}$watched"
    }

    companion object {
        /** Like the fit-check stream's own limit: the report call gets the time that's left. */
        private const val STREAM_SECONDS = 180L

        /** About $0.30 normally: about $0.03 per fit check. */
        private val ledger = RedTeamLedger("fit check", costLimitUsd = BigDecimal("0.50"))

        @JvmStatic
        fun cases(): List<Arguments> = RedTeamCases.fitCases().map { Arguments.of(it.id, it) }

        @JvmStatic
        @AfterAll
        fun printSummary() {
            println(ledger.summary())
            ledger.assertWithinCostLimit()
        }
    }
}