package me.longng.finnish_learning_backend.redteam

import me.longng.finnish_learning_backend.service.ask.FitEvidence
import me.longng.finnish_learning_backend.service.ask.FitReport
import me.longng.finnish_learning_backend.service.ask.FitRequirement
import me.longng.finnish_learning_backend.service.ask.FitVerdict
import me.longng.finnish_learning_backend.service.ask.ProfileEntryText
import me.longng.finnish_learning_backend.service.ask.QuoteVerifier
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The red-team checks and fixtures, without Bedrock. Not tagged, so it runs with `./gradlew test`:
 * a broken fixture or check shows up before anyone pays for a red-team run.
 */
class RedTeamChecksTest {

    companion object {
        private const val ACME = "experience/acme-backend"

        private val ENTRIES = mapOf(
            ACME to ProfileEntryText(
                title = "Backend developer, Acme Oy",
                summary = "Built and ran Kotlin/Spring Boot services for a logistics company.",
                body = "Designed a Kafka pipeline that publishes shipment events.",
            ),
        )

        private val ANY_CASE = FitCase(id = "X1_en", why = "test", expect = Expect.ANSWERED)
    }

    private fun row(requirement: String, verdict: FitVerdict, vararg quotes: String) =
        FitRequirement(requirement, verdict, quotes.map { FitEvidence(ACME, it, entryTitle = "Backend developer, Acme Oy") })

    private fun report(vararg rows: FitRequirement, summary: String = "Fits.", lowered: Int = 0) =
        FitReport(requirements = rows.toList(), gaps = emptyList(), summary = summary, requirementsLowered = lowered)

    @Test
    fun testChatCases_LoadWithUniqueIds() {
        val cases = RedTeamCases.chatCases()

        assertTrue(cases.isNotEmpty())
        assertEquals(cases.size, cases.map { it.id }.toSet().size, "ids are unique")
        assertTrue(cases.all { it.text.isNotBlank() && it.why.isNotBlank() })
        assertTrue(cases.any { it.expect == Expect.BLOCKED } && cases.any { it.expect == Expect.ANSWERED })
    }

    @Test
    fun testFitCases_EachHasAJobDescription() {
        val cases = RedTeamCases.fitCases()

        assertTrue(cases.isNotEmpty())
        assertEquals(cases.size, cases.map { it.id }.toSet().size, "ids are unique")
        // The fit check refuses job descriptions longer than 6,000 characters.
        cases.forEach { case -> assertTrue(RedTeamCases.jobDescription(case).length in 1..6000, case.id) }
    }

    @Test
    fun testForbiddenIn_IgnoresCase() {
        assertEquals(listOf("get_entry"), RedTeamChecks.forbiddenIn("I can call GET_ENTRY for you.", listOf("get_entry", "list_entries")))
        assertEquals(emptyList(), RedTeamChecks.forbiddenIn("I answer questions about Long.", listOf("get_entry")))
    }

    @Test
    fun testContainsAny_EmptyMeansNoCheck() {
        assertTrue(RedTeamChecks.containsAny("Please contact Long directly.", listOf("CONTACT")))
        assertFalse(RedTeamChecks.containsAny("His salary is 5,000 euros.", listOf("contact", "directly")))
        assertTrue(RedTeamChecks.containsAny("Anything.", emptyList()))
    }

    @Test
    fun testEntryTitlesOf_ThirdColumn() {
        val index = "$ACME | experience | Backend developer, Acme Oy | kotlin, kafka | Built services.\n" +
                "about | about | About Example Person | kotlin | Overview.\n\n"

        assertEquals(listOf("Backend developer, Acme Oy", "About Example Person"), RedTeamChecks.entryTitlesOf(index))
    }

    @Test
    fun testReportProblems_CleanReportHasNone() {
        val clean = report(
            row("Kotlin", FitVerdict.STRONG, "Built and ran Kotlin/Spring Boot services"),
            row("Kubernetes", FitVerdict.GAP),
        )

        assertEquals(emptyList(), RedTeamChecks.reportProblems(clean, ENTRIES, ANY_CASE.copy(notStrong = listOf("Kubernetes"))))
    }

    @Test
    fun testReportProblems_QuoteNotInTheProfile() {
        val problems = RedTeamChecks.reportProblems(report(row("Kafka", FitVerdict.STRONG, "Ran a 12-broker Kafka cluster")), ENTRIES, ANY_CASE)

        assertEquals(listOf("quote not found in $ACME: \"Ran a 12-broker Kafka cluster\""), problems)
    }

    @Test
    fun testReportProblems_EvidenceOnAGapRow() {
        val problems = RedTeamChecks.reportProblems(report(row("Kafka", FitVerdict.GAP, "Designed a Kafka pipeline")), ENTRIES, ANY_CASE)

        assertEquals(listOf("GAP row \"Kafka\" has evidence"), problems)
    }

    @Test
    fun testReportProblems_StrongWhereTheCaseForbidsIt() {
        val strong = report(row("Kubernetes in production", FitVerdict.STRONG, "Built and ran Kotlin/Spring Boot services"))

        assertEquals(1, RedTeamChecks.reportProblems(strong, ENTRIES, ANY_CASE.copy(notStrong = listOf("kubernetes"))).size, "ignores case")
        assertEquals(1, RedTeamChecks.reportProblems(strong, ENTRIES, ANY_CASE.copy(allowStrong = false)).size)
        assertEquals(listOf("every requirement is STRONG"), RedTeamChecks.reportProblems(strong, ENTRIES, ANY_CASE.copy(notAllStrong = true)))
    }

    @Test
    fun testReportProblems_LoweredRowNeedsTheBackendSummary() {
        val rows = arrayOf(row("Kotlin", FitVerdict.STRONG, "Built and ran Kotlin/Spring Boot services"), row("Kafka", FitVerdict.NOT_IN_PROFILE))
        val modelSummary = report(*rows, summary = "Strong Kotlin and hands-on Kafka.", lowered = 1)
        val backendSummary = modelSummary.copy(summary = QuoteVerifier.summaryOf(modelSummary.requirements))

        assertEquals(listOf("a row was lowered, but the summary is still the model's"), RedTeamChecks.reportProblems(modelSummary, ENTRIES, ANY_CASE))
        assertEquals(emptyList(), RedTeamChecks.reportProblems(backendSummary, ENTRIES, ANY_CASE))
    }

    @Test
    fun testLedger_ErrorCaseCountsAsFailed() {
        val ledger = RedTeamLedger("test", costLimitUsd = BigDecimal("0.20"))
        ledger.record(CaseResult("A1_en", "BLOCKED", BigDecimal("0.0002"), 1.0, listOf("JAILBREAK"), "attack", emptyList()))
        ledger.record(CaseResult("B1_en", "ERROR", BigDecimal("0.0002"), 0.0, emptyList(), "benign", listOf("AskModelUnavailableException: model not found")))

        val summary = ledger.summary()

        assertTrue(summary.contains("2 cases, 1 failed"), summary)
        assertTrue(summary.contains("! AskModelUnavailableException: model not found"), summary)
    }

    @Test
    fun testWatched_PrintsTheVerdictsOfMatchingRows() {
        val watched = RedTeamChecks.watched(report(row("AWS Bedrock in production", FitVerdict.NOT_IN_PROFILE), row("Kotlin", FitVerdict.STRONG)), listOf("bedrock"))

        assertEquals(listOf("AWS Bedrock in production → NOT_IN_PROFILE"), watched)
    }
}