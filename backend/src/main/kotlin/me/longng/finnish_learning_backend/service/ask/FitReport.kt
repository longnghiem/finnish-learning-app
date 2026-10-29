package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.service.ask.mcp.McpTool

/** How well the profile covers one requirement of a job description. */
enum class FitVerdict {
    /** A quote shows Long did this, at the level the job asks for. */
    STRONG,

    /** Related, at a lower level, or shown only indirectly. */
    PARTIAL,

    /** The profile says this is not done. */
    GAP,

    /** The profile has no evidence either way. */
    NOT_IN_PROFILE,
}

/**
 * One piece of evidence for a requirement: a quote, word for word, from a profile entry.
 *
 * @property entryId the profile entry the quote comes from, e.g. `experience/acme-backend`
 * @property quote the words copied from the entry's title, summary or body
 * @property entryTitle the entry's title, e.g. "Backend developer, Acme Oy", for the visitor to
 *   see where the quote comes from. Null in the model's draft: the model only names the id, and
 *   [QuoteVerifier] adds the title from the entry it checked the quote against
 */
data class FitEvidence(
    val entryId: String,
    val quote: String,
    val entryTitle: String? = null,
)

/**
 * One requirement of the job description and how the profile covers it.
 *
 * @property requirement the requirement in a few words, e.g. "Hands-on Kafka in production"
 * @property evidence the quotes behind [verdict]; empty for [FitVerdict.GAP] and
 *   [FitVerdict.NOT_IN_PROFILE]
 */
data class FitRequirement(
    val requirement: String,
    val verdict: FitVerdict,
    val evidence: List<FitEvidence>,
)

/**
 * The result of a fit check: one row per requirement, the gaps, and a short summary. There is no
 * score on purpose: a percentage would look precise without being it.
 *
 * The model writes the report through [FitReportTool]; [QuoteVerifier] then checks every quote,
 * so a report the visitor sees only holds quotes that really are in the profile.
 *
 * @property requirements at most [MAX_REQUIREMENTS] rows
 * @property gaps requirements the profile doesn't meet, at most [MAX_GAPS]. May name things that
 *   have no row in [requirements], so show the list on its own
 * @property summary the model's overall view; when [requirementsLowered] is above 0, a plain
 *   summary of the checked rows instead ([QuoteVerifier.summaryOf]), so it never claims a
 *   requirement the check lowered
 * @property unverifiedQuotesRemoved how many of the model's quotes weren't found word for word in
 *   the profile and were removed; 0 in the model's own draft
 * @property requirementsLowered how many STRONG or PARTIAL rows had no verified quote left and
 *   became NOT_IN_PROFILE; 0 in the model's own draft
 */
data class FitReport(
    val requirements: List<FitRequirement>,
    val gaps: List<String>,
    val summary: String,
    val unverifiedQuotesRemoved: Int = 0,
    val requirementsLowered: Int = 0,
) {
    companion object {
        /** The most requirement rows a report keeps; the prompt asks for at most this many. */
        const val MAX_REQUIREMENTS = 12

        /** The most gaps a report keeps. */
        const val MAX_GAPS = 12

        /**
         * Reads the model's draft: the input of its `submit_fit_report` call, as plain values
         * (see [toPlainValue]). Bedrock already made it match [FitReportTool.SCHEMA]; this turns
         * it into Kotlin types and applies the limits the schema can't: rows and gaps with blank
         * text are dropped, and only the first [MAX_REQUIREMENTS] rows and [MAX_GAPS] gaps are kept.
         *
         * @throws IllegalArgumentException if a field is missing or has the wrong type
         */
        fun fromToolInput(input: Map<String, Any?>): FitReport = FitReport(
            requirements = input.listOf("requirements").map { row ->
                val requirement = row.asObject("requirements[]")
                FitRequirement(
                    requirement = requirement.string("requirement").trim(),
                    verdict = FitVerdict.valueOf(requirement.string("verdict")),
                    evidence = requirement.listOf("evidence").map { item ->
                        val evidence = item.asObject("evidence[]")
                        FitEvidence(entryId = evidence.string("entryId"), quote = evidence.string("quote"))
                    },
                )
            }
                .filter { it.requirement.isNotEmpty() }
                .take(MAX_REQUIREMENTS),
            gaps = input.listOf("gaps")
                .map { it as? String ?: throw IllegalArgumentException("gaps[] must be strings") }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .take(MAX_GAPS),
            summary = input.string("summary"),
        )

        private fun Map<String, Any?>.string(key: String): String =
            this[key] as? String ?: throw IllegalArgumentException("$key must be a string")

        private fun Map<String, Any?>.listOf(key: String): List<Any?> =
            this[key] as? List<*> ?: throw IllegalArgumentException("$key must be a list")

        @Suppress("UNCHECKED_CAST")
        private fun Any?.asObject(name: String): Map<String, Any?> =
            this as? Map<String, Any?> ?: throw IllegalArgumentException("$name must be an object")
    }
}

/**
 * The `submit_fit_report` tool: the "form" the model fills in to hand over the fit report. The
 * report call forces it as a strict tool ([BedrockChatClient.converse] with `forcedTool`), so
 * Bedrock guarantees the input matches [SCHEMA], and the model can't call any other tool.
 *
 * Keep [SCHEMA] in sync with [FitReport.fromToolInput] and with the report schema of the
 * frontend (`frontend/src/schemas/ask.ts`). Bedrock compiles a new schema on first use (≈ 10 s
 * for this one) and keeps it for 24 hours, so changing it makes the next report slower once.
 */
object FitReportTool {
    const val NAME = "submit_fit_report"

    /**
     * The report's JSON Schema. Strict tools need `additionalProperties: false` and every
     * property listed in `required`, on every object.
     */
    val SCHEMA: Map<String, Any?> = mapOf(
        "type" to "object",
        "additionalProperties" to false,
        "required" to listOf("requirements", "gaps", "summary"),
        "properties" to mapOf(
            "requirements" to mapOf(
                "type" to "array",
                "items" to mapOf(
                    "type" to "object",
                    "additionalProperties" to false,
                    "required" to listOf("requirement", "verdict", "evidence"),
                    "properties" to mapOf(
                        "requirement" to mapOf("type" to "string"),
                        "verdict" to mapOf("type" to "string", "enum" to FitVerdict.entries.map { it.name }),
                        "evidence" to mapOf(
                            "type" to "array",
                            "items" to mapOf(
                                "type" to "object",
                                "additionalProperties" to false,
                                "required" to listOf("entryId", "quote"),
                                "properties" to mapOf(
                                    "entryId" to mapOf("type" to "string"),
                                    "quote" to mapOf("type" to "string"),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            "gaps" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
            "summary" to mapOf("type" to "string"),
        ),
    )

    /** The tool as the report call offers it. */
    val TOOL = McpTool(
        name = NAME,
        description = "Submit the final per-requirement fit report.",
        inputSchema = SCHEMA,
    )
}