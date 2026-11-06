package me.longng.finnish_learning_backend.service.ask

import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper

/**
 * The parts of one profile entry a fit-report quote may come from: its title, its summary (the
 * same text as in the profile index) and its body. The owner wrote all three, so quoting any of
 * them is honest.
 */
data class ProfileEntryText(
    val title: String,
    val summary: String,
    val body: String,
) {
    companion object {
        /**
         * Reads a successful `get_entry` result: the whole entry as JSON (`id`, `title`,
         * `summary`, `body`, …). Returns null if [json] isn't an entry.
         */
        fun fromGetEntryResult(json: String, objectMapper: ObjectMapper): ProfileEntryText? {
            val entry = try {
                objectMapper.readValue(json, Map::class.java)
            } catch (ex: JacksonException) {
                return null
            }
            val title = entry["title"] as? String ?: return null
            val summary = entry["summary"] as? String ?: return null
            val body = entry["body"] as? String ?: return null
            return ProfileEntryText(title = title, summary = summary, body = body)
        }
    }
}

/**
 * Checks the quotes of a fit report against the profile, so the visitor only sees evidence that
 * really is there. The model wrote the quotes; nothing it says is trusted as is.
 *
 * A quote counts as verified when, after [normalise] on both sides, it is at least
 * [MIN_QUOTE_CHARS] long and appears word for word in the title, the summary or the body of the
 * entry it names. It must fit inside one of the three: a quote that starts in the summary and
 * ends in the body is not in the profile as written. An unknown entry id fails.
 *
 * Then the rows are made consistent. A verdict is only ever lowered, never raised:
 * - [FitVerdict.STRONG] and [FitVerdict.PARTIAL] keep only their verified quotes. With none left
 *   (or none to begin with), the row becomes [FitVerdict.NOT_IN_PROFILE]: no evidence, no claim;
 * - [FitVerdict.GAP] and [FitVerdict.NOT_IN_PROFILE] carry no evidence. Any quotes the model
 *   attached anyway are dropped: a "not in the profile" row with quotes contradicts itself.
 *
 * Every quote that passes gets its entry's title ([FitEvidence.entryTitle]), so the visitor sees
 * "Backend developer, Acme Oy" instead of an id. A row keeps at most [MAX_QUOTES_PER_REQUIREMENT]
 * of its verified quotes. [FitReport.unverifiedQuotesRemoved] counts the quotes that failed, for a
 * footnote in the UI.
 *
 * The model wrote [FitReport.summary] and [FitReport.gaps] before the check, so they may still
 * claim a requirement the check lowered, e.g. "hands-on Kafka in production" above a Kafka row
 * that is now "not in profile". So when a row was lowered, its requirement is added to the gaps,
 * and the model's summary is replaced by [summaryOf] the checked rows: plainer, but it can't
 * contradict them. [FitReport.requirementsLowered] counts the lowered rows.
 */
object QuoteVerifier {

    /** Shorter quotes ("Kotlin", "Kafka") would match almost anywhere and prove nothing. */
    const val MIN_QUOTE_CHARS = 12

    /** The most verified quotes one requirement keeps; more add nothing for the visitor. */
    const val MAX_QUOTES_PER_REQUIREMENT = 3

    /** Returns [draft] with every quote checked against [entries] (by entry id). */
    fun verify(draft: FitReport, entries: Map<String, ProfileEntryText>): FitReport {
        val checked = draft.requirements.map { row -> checkRow(row, entries) }
        val lowered = checked.filter { it.lowered }.map { it.row.requirement }
        val knownGaps = draft.gaps.mapTo(HashSet()) { it.trim().lowercase() }
        val rows = checked.map { it.row }
        return draft.copy(
            requirements = rows,
            gaps = (draft.gaps + lowered.filter { it.trim().lowercase() !in knownGaps }).take(FitReport.MAX_GAPS),
            summary = if (lowered.isEmpty()) draft.summary else summaryOf(rows),
            unverifiedQuotesRemoved = checked.sumOf { it.failedQuotes },
            requirementsLowered = lowered.size,
        )
    }

    /**
     * A summary built from [rows] alone, e.g. "Matches: Kotlin. Partly matches: Spring Cloud. Not
     * shown in the profile: Kafka in production, Kubernetes." Used instead of the model's summary
     * when the check lowered a row. Always in English: the backend can't write in the job
     * description's language the way the model does.
     */
    internal fun summaryOf(rows: List<FitRequirement>): String {
        fun sentence(label: String, verdicts: Set<FitVerdict>): String? =
            rows.filter { it.verdict in verdicts }
                .takeIf { it.isNotEmpty() }
                ?.joinToString(", ", prefix = "$label: ", postfix = ".") { it.requirement }
        return listOfNotNull(
            sentence("Matches", setOf(FitVerdict.STRONG)),
            sentence("Partly matches", setOf(FitVerdict.PARTIAL)),
            sentence("Not shown in the profile", setOf(FitVerdict.GAP, FitVerdict.NOT_IN_PROFILE)),
        ).joinToString(" ")
    }

    /** True if [evidence]'s quote is in the entry it names, as described on [QuoteVerifier]. */
    fun isVerbatim(evidence: FitEvidence, entries: Map<String, ProfileEntryText>): Boolean {
        val entry = entries[evidence.entryId] ?: return false
        val quote = normaliseQuote(evidence.quote)
        if (quote.length < MIN_QUOTE_CHARS) return false
        return listOf(entry.title, entry.summary, entry.body).any { field -> normalise(field).contains(quote) }
    }

    /**
     * Makes two texts comparable without changing their words. Applied to both sides:
     * - curly quotes become straight ones, and the dashes `–` `—` `−` become `-`: the model often
     *   writes the other kind;
     * - `*` and backticks are removed: they are markdown marks in the entry body, not words;
     * - `|` becomes `/`, as in the profile index, where `|` separates the fields;
     * - every run of whitespace (line breaks included) becomes one space, and the ends are trimmed.
     *   Unicode spaces count too, such as the non-breaking space that text pasted from a word
     *   processor often holds: the profile index turns them into plain spaces, so the model quotes
     *   them that way.
     *
     * Letter case is kept: "java" is not the same claim as "Java".
     */
    fun normalise(text: String): String =
        text.map { char -> CHAR_REPLACEMENTS[char] ?: char.toString() }
            .joinToString("")
            .replace(WHITESPACE, " ")
            .trim()

    /**
     * [normalise], then removes what the model adds around a quote: quotation marks at both ends
     * and a final `.`, `,`, `;` or `:` (the sentence in the entry may go on after the quote).
     */
    private fun normaliseQuote(quote: String): String =
        normalise(quote).trim('"', '\'').trimEnd('.', ',', ';', ':').trim()

    private fun checkRow(row: FitRequirement, entries: Map<String, ProfileEntryText>): CheckedRow = when (row.verdict) {
        FitVerdict.GAP, FitVerdict.NOT_IN_PROFILE -> CheckedRow(row.copy(evidence = emptyList()), failedQuotes = 0, lowered = false)
        FitVerdict.STRONG, FitVerdict.PARTIAL -> {
            val verified = row.evidence
                .filter { isVerbatim(it, entries) }
                .map { it.copy(entryTitle = entries.getValue(it.entryId).title) }
            val failed = row.evidence.size - verified.size
            if (verified.isEmpty()) {
                CheckedRow(row.copy(verdict = FitVerdict.NOT_IN_PROFILE, evidence = emptyList()), failed, lowered = true)
            } else {
                CheckedRow(row.copy(evidence = verified.take(MAX_QUOTES_PER_REQUIREMENT)), failed, lowered = false)
            }
        }
    }

    /**
     * @property failedQuotes the row's quotes that weren't found in the profile
     * @property lowered true if the row was STRONG or PARTIAL and has no verified quote left
     */
    private class CheckedRow(val row: FitRequirement, val failedQuotes: Int, val lowered: Boolean)

    /** Any run of whitespace, Unicode space separators (`\p{Z}`, e.g. U+00A0) included. */
    private val WHITESPACE = Regex("[\\s\\p{Z}]+")

    private val CHAR_REPLACEMENTS: Map<Char, String> = mapOf(
        '‘' to "'", '’' to "'", '‚' to "'", '‛' to "'",
        '“' to "\"", '”' to "\"", '„' to "\"", '‟' to "\"",
        '–' to "-", '—' to "-", '−' to "-",
        '*' to "", '`' to "",
        '|' to "/",
    )
}