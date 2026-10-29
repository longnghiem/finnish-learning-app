package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.config.AskProperties
import me.longng.finnish_learning_backend.domain.ask.AskMode
import me.longng.finnish_learning_backend.domain.ask.AskSession
import me.longng.finnish_learning_backend.domain.ask.ToolCall
import me.longng.finnish_learning_backend.domain.ask.TurnStatus
import me.longng.finnish_learning_backend.service.ask.mcp.McpProfileClient
import me.longng.finnish_learning_backend.service.ask.mcp.McpProfileUnavailableException
import me.longng.finnish_learning_backend.service.ask.mcp.McpTool
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.bedrockruntime.model.Message
import software.amazon.awssdk.services.bedrockruntime.model.StopReason
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Checks a job description against Long's profile: one verdict per requirement, each backed by
 * quotes from the profile that the backend has verified. No score: a percentage would look
 * precise without being it, and it invites overselling.
 *
 * The order of the steps is the same as in [ChatService], and for the same reason, money: free
 * checks first, then the budget reserve, then the guardrail (the first paid call), so a blocked
 * job description costs only the guardrail.
 *
 * The model works in two phases:
 * 1. Research: up to [AskProperties.Agent.fitMaxToolRounds] calls with a short reply limit
 *    ([AskProperties.Agent.fitStepMaxTokens]). The model reads entries through the profile
 *    tools ([ProfileToolRunner]). Research ends when the model asks for no more tools, when the
 *    round limit is reached, or when a reply is cut off by its limit: a cut-off reply may hold a
 *    half-written tool request, so it is dropped and nothing of it runs.
 * 2. Report: exactly one call with the long reply limit
 *    ([AskProperties.Agent.fitReportMaxTokens]) that must call [FitReportTool] (forced, strict,
 *    temperature 0). Only this call gets the long limit, which keeps the worst-case reserve low.
 *
 * Then [QuoteVerifier] checks every quote against the cited entry's title, summary and body, read
 * with `get_entry`: entries the research already read are reused, the others are read now, if the
 * profile index lists them. A verdict is only ever lowered by the check, never raised.
 *
 * The caller's deadline (when the visitor's stream closes) caps the report call's timeout, so a
 * report is never paid for when it can't be shown any more.
 *
 * The job description goes to the model inside `<job_description>` tags, and the prompt says that
 * everything inside is data. The report is stored with the turn as JSON. Logs never carry the job
 * description or the report.
 */
@Service
class FitCheckService(
    private val recorder: AskTurnRecorder,
    private val rateLimiter: AskRateLimiter,
    private val costCalculator: CostCalculator,
    private val guardrailService: GuardrailService,
    private val profileClient: McpProfileClient,
    private val toolRunner: ProfileToolRunner,
    private val chatClient: BedrockChatClient,
    private val objectMapper: ObjectMapper,
    properties: AskProperties,
    @Value("classpath:prompts/recruiter-fit-check-system-prompt.md") systemPromptResource: Resource,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val logger = LoggerFactory.getLogger(FitCheckService::class.java)

    private val agent = properties.agent
    private val settings = properties.fitCheck

    /** Read at startup: a missing prompt file stops the app instead of the first fit check. */
    private val basePrompt: String = systemPromptResource.getContentAsString(Charsets.UTF_8).trim()

    /**
     * The checks [check] makes before anything is stored or spent. The caller may run them first
     * on the request thread, so these refusals become plain HTTP errors; [check] runs them again.
     *
     * @throws IllegalArgumentException if [jobDescription] is blank or longer than
     *   [AskProperties.FitCheck.maxChars]
     * @throws AskRateLimitedException if the visitor's IP reached its daily fit-check limit
     */
    fun checkCanCheck(session: AskSession, jobDescription: String) {
        require(jobDescription.isNotBlank()) { "The job description must not be blank" }
        require(jobDescription.length <= settings.maxChars) {
            "The job description must be at most ${settings.maxChars} characters"
        }
        rateLimiter.checkMessage(session.ipHash, AskMode.FIT_CHECK)
    }

    /**
     * Checks [jobDescription] against the profile, reporting progress to [listener].
     *
     * The session must already be validated. The refusals of [checkCanCheck] come before anything
     * is stored. After that, the turn is stored and ends as one of:
     *
     * @param deadline when the result can't be delivered any more, e.g. when the visitor's stream
     *   times out
     * @throws AskBudgetExhaustedException if a spending cap is full; the turn is CAPPED
     * @throws AskInputBlockedException if the guardrail refused the job description; the turn is BLOCKED
     * @throws GuardrailUnavailableException if the guardrail check failed; the turn is ERROR
     * @throws McpProfileUnavailableException if the profile server failed during research; the turn
     *   is ERROR
     * @throws AskModelUnavailableException if a model call failed, too little time was left for the
     *   report call, or it returned no complete report; the turn is ERROR
     */
    fun check(
        session: AskSession,
        jobDescription: String,
        deadline: Instant,
        listener: AskProgressListener,
    ): FitCheckResult {
        checkCanCheck(session, jobDescription)
        val turn = recorder.start(session.id, AskMode.FIT_CHECK, jobDescription)
        val outcome = try {
            run(turn, jobDescription, deadline, listener)
        } catch (ex: Exception) {
            recorder.recordFailure(turn, ex)
            throw ex
        }
        return when (outcome) {
            is Outcome.Reported -> FitCheckResult(turnId = turn.id, report = outcome.report, toolTrace = turn.toolTrace)
            is Outcome.Blocked -> throw AskInputBlockedException(outcome.categories)
        }
    }

    /** Everything after the turn is stored. Records the turn itself when it is answered or blocked. */
    private fun run(turn: TurnInProgress, jobDescription: String, deadline: Instant, listener: AskProgressListener): Outcome {
        val index = profileClient.readIndex()
        val system = "$basePrompt\n\n${ChatService.PROFILE_INDEX_HEADING}\n$index"
        val tools = profileClient.listTools()
        val reservation = recorder.reserve(
            turn,
            costCalculator.estimateMax(
                AskMode.FIT_CHECK,
                promptChars = chatClient.promptChars(system, tools + FitReportTool.TOOL),
                inputChars = jobDescription.length,
                historyChars = 0,
            ),
        )

        val guardrail = guardrailService.check(AskMode.FIT_CHECK, jobDescription)
        turn.guardrail = guardrail
        val verdict = guardrail.verdict
        if (verdict is GuardrailVerdict.Blocked) {
            recorder.finish(turn, TurnStatus.BLOCKED)
            recorder.countOutcome(turn, reservation.day, blocked = 1)
            logger.info("Fit check blocked: turnId={} categories={}", turn.id, verdict.categories)
            return Outcome.Blocked(verdict.categories)
        }
        listener.onStep(AskStep.GuardrailPassed)

        val research = research(turn, system, tools, delimit(jobDescription), listener)
        val draft = writeReport(turn, system, tools, research.messages, deadline, listener)

        listener.onStep(AskStep.Verifying)
        val cited = readCitedEntries(turn, draft, alreadyRead = research.entries.keys, indexIds = entryIdsOf(index))
        val report = QuoteVerifier.verify(draft, research.entries + cited)
        recorder.finish(turn, TurnStatus.ANSWERED, fitReportJson = objectMapper.writeValueAsString(report))
        recorder.countOutcome(turn, reservation.day, fitChecks = 1)
        logger.info(
            "Fit check answered: turnId={} rounds={} toolCalls={} requirements={} quotesRemoved={} " +
                    "requirementsLowered={} inputTokens={} outputTokens={} cacheReadTokens={} cacheWriteTokens={} " +
                    "costUsd={} latencyMs={}",
            turn.id,
            turn.rounds,
            turn.toolTrace.size,
            report.requirements.size,
            report.unverifiedQuotesRemoved,
            report.requirementsLowered,
            turn.tokens.inputTokens,
            turn.tokens.outputTokens,
            turn.tokens.cacheReadTokens,
            turn.tokens.cacheWriteTokens,
            recorder.costOf(turn),
            turn.latencyMs(),
        )
        return Outcome.Reported(report)
    }

    /**
     * The research phase. Returns the conversation to write the report from (the job description,
     * then each tool request with its results) and the entries read, for the quote check.
     */
    private fun research(
        turn: TurnInProgress,
        system: String,
        tools: List<McpTool>,
        jobDescriptionText: String,
        listener: AskProgressListener,
    ): Research {
        var messages = listOf(BedrockChatClient.userText(jobDescriptionText))
        var entries = emptyMap<String, ProfileEntryText>()
        while (turn.rounds < agent.fitMaxToolRounds) {
            val reply = chatClient.converse(system, messages, tools, agent.fitStepMaxTokens)
            turn.tokens += reply.usage
            if (!reply.wantsTools) {
                // Done, or cut off by the reply limit. Either way the reply is left out: the model's
                // text isn't needed, and a cut-off reply may hold a half-written tool request.
                if (reply.stopReason == StopReason.MAX_TOKENS) {
                    logger.info("Fit check research reply cut off, research ends: turnId={}", turn.id)
                }
                return Research(messages, entries)
            }
            turn.rounds += 1
            val results = toolRunner.runRound(turn, reply.toolRequests, tools, listener)
            entries = entries + entriesRead(reply.toolRequests.zip(results))
            messages = messages + reply.message + BedrockChatClient.toolResults(results)
        }
        return Research(messages, entries)
    }

    /**
     * The report phase: one forced call of [FitReportTool]. The "research is finished" note is
     * added to the last user message, because Bedrock wants user and assistant messages to take
     * turns.
     *
     * @throws AskModelUnavailableException if too little time is left before [deadline], or the
     *   reply holds no complete report
     */
    private fun writeReport(
        turn: TurnInProgress,
        system: String,
        tools: List<McpTool>,
        messages: List<Message>,
        deadline: Instant,
        listener: AskProgressListener,
    ): FitReport {
        val timeoutMs = reportTimeoutMs(deadline)
        listener.onStep(AskStep.Composing)
        val withNote = messages.dropLast(1) + BedrockChatClient.withText(messages.last(), REPORT_NOTE)
        val reply = chatClient.converse(
            system,
            withNote,
            tools,
            agent.fitReportMaxTokens,
            forcedTool = FitReportTool.TOOL,
            timeoutMs = timeoutMs,
        )
        turn.tokens += reply.usage
        if (reply.stopReason == StopReason.MAX_TOKENS) {
            // A strict tool's input only matches its schema when the model could finish it.
            throw AskModelUnavailableException("The fit report was cut off at ${agent.fitReportMaxTokens} tokens")
        }
        val input = reply.toolRequests.singleOrNull { it.name == FitReportTool.NAME }?.arguments
            ?: throw AskModelUnavailableException("The report call returned no ${FitReportTool.NAME} call")
        return try {
            FitReport.fromToolInput(input)
        } catch (ex: IllegalArgumentException) {
            throw AskModelUnavailableException("The fit report doesn't match its schema: ${ex.message}", ex)
        }
    }

    /**
     * The report call's timeout: [AskProperties.FitCheck.reportTimeoutMs], or less if [deadline]
     * is nearer, keeping [REPORT_END_MARGIN] for the quote check and for sending the report.
     *
     * @throws AskModelUnavailableException if less than [MIN_REPORT_TIMEOUT] is left: even a fast
     *   report wouldn't arrive in time, so the call isn't made at all
     */
    private fun reportTimeoutMs(deadline: Instant): Long {
        val left = Duration.between(clock.instant(), deadline).minus(REPORT_END_MARGIN)
        if (left < MIN_REPORT_TIMEOUT) {
            throw AskModelUnavailableException("Too little time left for the fit report: ${left.toMillis()} ms")
        }
        return minOf(settings.reportTimeoutMs, left.toMillis())
    }

    /** The entries that successful `get_entry` calls of one round returned, by id. */
    private fun entriesRead(round: List<Pair<ModelToolRequest, ToolOutcome>>): Map<String, ProfileEntryText> =
        round
            .filter { (request, result) -> request.name == GET_ENTRY && !result.isError }
            .mapNotNull { (request, result) ->
                val id = request.arguments["id"] as? String
                val entry = ProfileEntryText.fromGetEntryResult(result.text, objectMapper)
                if (id != null && entry != null) id to entry else null
            }
            .toMap()

    /**
     * Reads the entries that [draft] quotes but the research didn't read, e.g. when the model
     * quoted a summary from the profile index. These reads are the backend's own, so they get no
     * step and no tool-trace entry.
     *
     * Only ids in [indexIds] are read: any other id is made up, and reading it would cost a call
     * for nothing. An entry that can't be read (unknown id, or the profile server failing now that
     * the report is paid for) stays missing, so its quotes fail: a verdict may then be lower than
     * it should be, never higher.
     */
    private fun readCitedEntries(
        turn: TurnInProgress,
        draft: FitReport,
        alreadyRead: Set<String>,
        indexIds: Set<String>,
    ): Map<String, ProfileEntryText> =
        draft.requirements
            .flatMap { row -> row.evidence.map { it.entryId } }
            .distinct()
            .filter { it !in alreadyRead && it in indexIds }
            .mapNotNull { id -> readEntry(turn, id)?.let { id to it } }
            .toMap()

    private fun readEntry(turn: TurnInProgress, id: String): ProfileEntryText? {
        val result = try {
            profileClient.callTool(GET_ENTRY, mapOf("id" to id))
        } catch (ex: McpProfileUnavailableException) {
            logger.warn("Fit check entry not read for the quote check: turnId={} error={}", turn.id, ex.javaClass.simpleName)
            return null
        }
        return if (result.isError) null else ProfileEntryText.fromGetEntryResult(result.text, objectMapper)
    }

    /**
     * @property messages the conversation to write the report from
     * @property entries the entries read during research, by id
     */
    private class Research(val messages: List<Message>, val entries: Map<String, ProfileEntryText>)

    /** How [run] ended, when it didn't throw. */
    private sealed interface Outcome {
        data class Reported(val report: FitReport) : Outcome
        data class Blocked(val categories: List<String>) : Outcome
    }

    companion object {
        /** The profile tool that reads one entry; its results are the text quotes are checked against. */
        private const val GET_ENTRY = "get_entry"

        /** Kept free after the report call for the quote check and for sending the report. */
        private val REPORT_END_MARGIN: Duration = Duration.ofSeconds(10)

        /** A warm report takes ≈ 8 s; with less time than this, the call isn't worth making. */
        private val MIN_REPORT_TIMEOUT: Duration = Duration.ofSeconds(15)

        /** Added to the last user message of the report call. */
        internal const val REPORT_NOTE =
            "Research is finished. Call submit_fit_report now, using only the profile index and the entries you have read."

        private val JOB_DESCRIPTION_TAG = Regex("</?\\s*job_description\\s*>", RegexOption.IGNORE_CASE)

        /**
         * The job description as the model's first message, inside `<job_description>` tags. Tags
         * of that name inside the text are removed first, so the text can't close the block early
         * and continue as "instructions".
         */
        internal fun delimit(jobDescription: String): String =
            "<job_description>\n${jobDescription.replace(JOB_DESCRIPTION_TAG, "").trim()}\n</job_description>"

        /**
         * The entry ids in [index], the profile index as [McpProfileClient.readIndex] returns it:
         * one line per entry, starting with the id and ` | ` (ids can't contain `|`).
         */
        internal fun entryIdsOf(index: String): Set<String> =
            index.lineSequence().map { it.substringBefore('|').trim() }.filter { it.isNotEmpty() }.toSet()
    }
}

/**
 * The result of one fit check, as [FitCheckService.check] returns it.
 *
 * @property turnId the stored turn, for the admin view and for log lines
 * @property report the verified report
 * @property toolTrace the profile tools the model used during research, shown as "How I found this"
 */
data class FitCheckResult(
    val turnId: Long,
    val report: FitReport,
    val toolTrace: List<ToolCall>,
)