package me.longng.finnish_learning_backend.service.ask

import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuoteVerifierTest {

    companion object {
        private const val ACME = "experience/acme-backend"

        private val ENTRIES = mapOf(
            ACME to ProfileEntryText(
                title = "Backend developer, Acme Oy",
                summary = "Built REST APIs and nightly batch jobs for a logistics planning platform.",
                body = """
                    Long built the **quiz answer** pipeline: the backend writes each answer to
                    PostgreSQL and publishes a `QuizAnswerEvent` to Kafka.
                    - Owned the Kafka consumers in production for two years.
                """.trimIndent(),
            ),
        )
    }

    private fun evidence(quote: String, entryId: String = ACME) = FitEvidence(entryId = entryId, quote = quote)

    private fun verified(quote: String, entryId: String = ACME) = QuoteVerifier.isVerbatim(evidence(quote, entryId), ENTRIES)

    private fun row(verdict: FitVerdict, vararg quotes: String) =
        FitRequirement(requirement = "Kafka in production", verdict = verdict, evidence = quotes.map { evidence(it) })

    private fun report(vararg rows: FitRequirement) = FitReport(requirements = rows.toList(), gaps = emptyList(), summary = "Fits.")

    @Test
    fun testVerify_ExactMatch() {
        assertTrue(verified("Owned the Kafka consumers in production for two years."))
    }

    @Test
    fun testVerify_WhitespaceNormalised() {
        // The body breaks the line after "to"; the model writes it on one line.
        assertTrue(verified("writes each answer to PostgreSQL and publishes"))
    }

    @Test
    fun testVerify_MarkdownMarksAndCurlyQuotesIgnored() {
        assertTrue(verified("built the quiz answer pipeline"), "** is markdown, not words")
        assertTrue(verified("publishes a QuizAnswerEvent to Kafka"), "backticks too")
        assertTrue(verified("“Built REST APIs and nightly batch jobs”"), "quotation marks around the quote")
    }

    @Test
    fun testVerify_ParaphraseFails() {
        assertFalse(verified("Ran Kafka consumers in production for two years."))
    }

    @Test
    fun testVerify_CaseMatters() {
        assertFalse(verified("owned the kafka consumers in production"))
    }

    @Test
    fun testVerify_UnknownEntryFails() {
        assertFalse(verified("Owned the Kafka consumers in production", entryId = "experience/unknown"))
    }

    @Test
    fun testVerify_QuoteFromOtherEntryFails() {
        val entries = ENTRIES + ("projects/other" to ProfileEntryText("Other", "Something else entirely.", "Nothing about Kafka."))

        assertFalse(QuoteVerifier.isVerbatim(evidence("Owned the Kafka consumers", "projects/other"), entries))
    }

    @Test
    fun testVerify_TooShortQuoteFails() {
        assertFalse(verified("Kafka"))
        assertFalse(verified("Kafka cons"), "10 characters")
        assertTrue(verified("Kafka consum"), "12 characters")
    }

    @Test
    fun testVerify_QuoteFromSummaryAccepted() {
        assertTrue(verified("nightly batch jobs for a logistics planning platform"))
    }

    @Test
    fun testVerify_QuoteFromTitleAccepted() {
        assertTrue(verified("Backend developer, Acme Oy"))
    }

    @Test
    fun testVerify_QuoteAcrossFieldsRejected() {
        // The end of the summary followed by the start of the body: both real, but not one text.
        assertFalse(verified("logistics planning platform. Long built the quiz answer pipeline"))
    }

    @Test
    fun testVerify_FailedQuoteDowngradesToNotInProfile() {
        val checked = QuoteVerifier.verify(report(row(FitVerdict.STRONG, "Led a team of twenty Kafka engineers.")), ENTRIES)

        val result = checked.requirements.single()
        assertEquals(FitVerdict.NOT_IN_PROFILE, result.verdict)
        assertEquals(emptyList(), result.evidence)
        assertEquals(1, checked.unverifiedQuotesRemoved)
    }

    @Test
    fun testVerify_MixedEvidenceKeepsOnlyVerifiedQuotes() {
        val checked = QuoteVerifier.verify(
            report(row(FitVerdict.PARTIAL, "Owned the Kafka consumers in production", "Tuned Kafka brokers for low latency")),
            ENTRIES,
        )

        val result = checked.requirements.single()
        assertEquals(FitVerdict.PARTIAL, result.verdict)
        assertEquals(listOf("Owned the Kafka consumers in production"), result.evidence.map { it.quote })
        assertEquals(1, checked.unverifiedQuotesRemoved)
    }

    @Test
    fun testVerify_VerifiedQuoteGetsTheEntryTitle() {
        val checked = QuoteVerifier.verify(report(row(FitVerdict.STRONG, "Owned the Kafka consumers in production")), ENTRIES)

        val evidence = checked.requirements.single().evidence.single()
        assertEquals(ACME, evidence.entryId)
        assertEquals("Backend developer, Acme Oy", evidence.entryTitle)
    }

    @Test
    fun testVerify_StrongWithoutEvidenceBecomesNotInProfile() {
        val checked = QuoteVerifier.verify(report(row(FitVerdict.STRONG)), ENTRIES)

        assertEquals(FitVerdict.NOT_IN_PROFILE, checked.requirements.single().verdict)
        assertEquals(0, checked.unverifiedQuotesRemoved)
    }

    @Test
    fun testVerify_NeverUpgradesVerdict() {
        // Real quotes on a NOT_IN_PROFILE row don't make it PARTIAL; they are dropped.
        val checked = QuoteVerifier.verify(
            report(row(FitVerdict.NOT_IN_PROFILE, "Owned the Kafka consumers in production"), row(FitVerdict.PARTIAL, "Owned the Kafka consumers in production")),
            ENTRIES,
        )

        assertEquals(listOf(FitVerdict.NOT_IN_PROFILE, FitVerdict.PARTIAL), checked.requirements.map { it.verdict })
    }

    @Test
    fun testVerify_EvidenceDroppedFromGapAndNotInProfileRows() {
        val checked = QuoteVerifier.verify(
            report(row(FitVerdict.GAP, "Owned the Kafka consumers in production"), row(FitVerdict.NOT_IN_PROFILE, "Made up quote here")),
            ENTRIES,
        )

        assertEquals(listOf(FitVerdict.GAP, FitVerdict.NOT_IN_PROFILE), checked.requirements.map { it.verdict })
        assertTrue(checked.requirements.all { it.evidence.isEmpty() })
        assertEquals(0, checked.unverifiedQuotesRemoved, "only quotes that should have held count")
    }

    @Test
    fun testVerify_KeepsGapsAndSummary() {
        val draft = report(row(FitVerdict.GAP)).copy(gaps = listOf("Kubernetes"), summary = "Partly fits.")

        val checked = QuoteVerifier.verify(draft, ENTRIES)

        assertEquals(listOf("Kubernetes"), checked.gaps)
        assertEquals("Partly fits.", checked.summary)
        assertEquals(0, checked.requirementsLowered)
    }

    @Test
    fun testVerify_NonBreakingSpaceInProfileMatchesPlainSpace() {
        // Text pasted from a word processor; the profile index turns U+00A0 into a plain space.
        val entries = mapOf(
            ACME to ProfileEntryText(title = "Backend developer", summary = "Cut p95 latency to 120\u00A0ms with indexes.", body = "Body."),
        )

        assertTrue(QuoteVerifier.isVerbatim(evidence("Cut p95 latency to 120 ms"), entries))
    }

    @Test
    fun testVerify_LoweredRowsAreAddedToGapsAndCounted() {
        val draft = FitReport(
            requirements = listOf(
                row(FitVerdict.STRONG, "Led a team of twenty Kafka engineers."),
                FitRequirement("Kubernetes", FitVerdict.PARTIAL, listOf(evidence("Ran Kubernetes clusters in production"))),
                FitRequirement("Go", FitVerdict.NOT_IN_PROFILE, emptyList()),
            ),
            gaps = listOf("kubernetes", "Go"),
            summary = "Strong Kafka match.",
        )

        val checked = QuoteVerifier.verify(draft, ENTRIES)

        assertEquals(2, checked.requirementsLowered, "the model's own NOT_IN_PROFILE isn't lowered")
        assertEquals(listOf("kubernetes", "Go", "Kafka in production"), checked.gaps, "a gap already listed isn't repeated")
        assertEquals(
            "Not shown in the profile: Kafka in production, Kubernetes, Go.",
            checked.summary,
            "the model's summary would still claim Kafka; it is replaced by one built from the checked rows",
        )
    }

    @Test
    fun testVerify_SummaryReplacedOnlyWhenARowIsLowered() {
        val draft = FitReport(
            requirements = listOf(
                FitRequirement("Kotlin", FitVerdict.STRONG, listOf(evidence("Owned the Kafka consumers in production"))),
                row(FitVerdict.STRONG, "Ran a 12-broker Kafka cluster"),
                FitRequirement("Kubernetes", FitVerdict.GAP, emptyList()),
            ),
            gaps = listOf("Kubernetes"),
            summary = "Strong match: solid Kotlin and hands-on Kafka in production. Only Kubernetes is missing.",
        )

        val lowered = QuoteVerifier.verify(draft, ENTRIES)
        val nothingLowered = QuoteVerifier.verify(draft.copy(requirements = draft.requirements - draft.requirements[1]), ENTRIES)

        assertEquals("Matches: Kotlin. Not shown in the profile: Kafka in production, Kubernetes.", lowered.summary)
        assertEquals(draft.summary, nothingLowered.summary, "the model's summary agrees with the rows, so it stays")
    }

    @Test
    fun testSummaryOf_OneSentencePerKindOfVerdict() {
        val rows = listOf(
            FitRequirement("Kotlin", FitVerdict.STRONG, emptyList()),
            FitRequirement("Spring Cloud", FitVerdict.PARTIAL, emptyList()),
            FitRequirement("Go", FitVerdict.NOT_IN_PROFILE, emptyList()),
            FitRequirement("PostgreSQL", FitVerdict.STRONG, emptyList()),
            FitRequirement("Kubernetes", FitVerdict.GAP, emptyList()),
        )

        assertEquals(
            "Matches: Kotlin, PostgreSQL. Partly matches: Spring Cloud. Not shown in the profile: Go, Kubernetes.",
            QuoteVerifier.summaryOf(rows),
        )
    }

    @Test
    fun testVerify_KeepsAtMostThreeVerifiedQuotes() {
        val checked = QuoteVerifier.verify(
            report(
                row(
                    FitVerdict.STRONG,
                    "Made-up quote about Kafka streams",
                    "Owned the Kafka consumers in production",
                    "publishes a QuizAnswerEvent to Kafka",
                    "writes each answer to PostgreSQL",
                    "Built REST APIs and nightly batch jobs",
                ),
            ),
            ENTRIES,
        )

        val result = checked.requirements.single()
        assertEquals(
            listOf("Owned the Kafka consumers in production", "publishes a QuizAnswerEvent to Kafka", "writes each answer to PostgreSQL"),
            result.evidence.map { it.quote },
            "a failed quote doesn't take one of the three places",
        )
        assertEquals(1, checked.unverifiedQuotesRemoved, "verified quotes beyond the limit aren't failures")
    }

    @Test
    fun testFromGetEntryResult_ReadsTheEntry() {
        val json = """{"id":"x","type":"project","title":"T","tags":[],"summary":"S","body":"B","skills":[]}"""

        assertEquals(ProfileEntryText("T", "S", "B"), ProfileEntryText.fromGetEntryResult(json, JsonMapper.builder().build()))
    }

    @Test
    fun testFromGetEntryResult_NotAnEntryIsNull() {
        val mapper = JsonMapper.builder().build()

        assertNull(ProfileEntryText.fromGetEntryResult("Unknown entry id: x", mapper))
        assertNull(ProfileEntryText.fromGetEntryResult("""{"title":"T"}""", mapper))
    }
}