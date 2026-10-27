package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.config.AskProperties
import me.longng.finnish_learning_backend.domain.ask.AskMode
import me.longng.finnish_learning_backend.domain.ask.AskSession
import me.longng.finnish_learning_backend.domain.ask.GuardrailOutcome
import me.longng.finnish_learning_backend.domain.ask.TokenUsage
import me.longng.finnish_learning_backend.domain.ask.ToolCall
import me.longng.finnish_learning_backend.domain.ask.TurnCompletion
import me.longng.finnish_learning_backend.domain.ask.TurnStatus
import me.longng.finnish_learning_backend.persistence.AskDailyStatsRepository
import me.longng.finnish_learning_backend.persistence.AskTurnRepository
import me.longng.finnish_learning_backend.service.ask.mcp.McpProfileClient
import me.longng.finnish_learning_backend.service.ask.mcp.McpProfileUnavailableException
import me.longng.finnish_learning_backend.service.ask.mcp.McpTool
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.bedrockruntime.model.Message
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Answers one recruiter chat message, from the checks to the stored turn.
 *
 * The order is about money. The cheap checks come first and cost nothing. The budget is
 * reserved before the guardrail, the first call that costs money, so even the guardrail can't
 * spend past a cap. A blocked message never reaches the model, so it costs only the guardrail.
 *
 * The tool loop. The model sees the system prompt with the profile index, the earlier
 * messages and answers, and the new message. It may ask for tools instead of answering: the
 * service runs at most [AskProperties.Agent.maxReadsPerRound] of them through [McpProfileClient]
 * (any extra request gets an error result), sends all results back in one message and calls the
 * model again. One such ask-and-read step is a round. After
 * [AskProperties.Agent.chatMaxToolRounds] rounds, the results go back with "Tool limit reached.
 * Answer now…", and that last call's text is the answer, even if it asks for more tools.
 *
 * The history comes from the database, never from the browser: the answered chat turns of the
 * session, as user and assistant messages. A visitor can't slip in a fake earlier answer.
 *
 * No token streaming. The visitor sees [AskStep]s while the answer is made, then the whole
 * answer (ADR 0002): token streaming would need Bedrock's async client and its Netty dependency,
 * too heavy for the small server.
 *
 * Every turn ends exactly once as ANSWERED, BLOCKED, CAPPED or ERROR, and its reservation is
 * settled with what was really spent, also when something fails halfway. Logs carry the turn id,
 * counts, tokens, cost and latency, never the message or the answer.
 */
@Service
class ChatService(
    private val turnRepository: AskTurnRepository,
    private val statsRepository: AskDailyStatsRepository,
    private val rateLimiter: AskRateLimiter,
    private val budgetService: BudgetService,
    private val costCalculator: CostCalculator,
    private val guardrailService: GuardrailService,
    private val profileClient: McpProfileClient,
    private val chatClient: BedrockChatClient,
    properties: AskProperties,
    @Value("classpath:prompts/recruiter-chat-system-prompt.md") systemPromptResource: Resource,
) {
    private val logger = LoggerFactory.getLogger(ChatService::class.java)

    private val sessionSettings = properties.session
    private val agent = properties.agent

    /** Read at startup: a missing prompt file stops the app instead of the first chat message. */
    private val basePrompt: String = systemPromptResource.getContentAsString(Charsets.UTF_8).trim()

    /**
     * The checks [answer] makes before anything is stored or spent. The caller may run them first
     * on the request thread, so these refusals become plain HTTP errors instead of stream events;
     * [answer] runs them again.
     *
     * @throws IllegalArgumentException if [text] is blank or longer than
     *   [AskProperties.Session.maxMessageChars]
     * @throws AskConversationLimitException if the session already has
     *   [AskProperties.Session.maxMessages] chat messages that were answered, blocked or are still
     *   running. Failed ones ([TurnStatus.ERROR], [TurnStatus.CAPPED]) don't count: the visitor got
     *   no answer, and a retry while Bedrock is down shouldn't use up the conversation
     * @throws AskRateLimitedException if the visitor's IP reached its daily chat limit
     */
    fun checkCanAsk(session: AskSession, text: String) {
        require(text.isNotBlank()) { "The message must not be blank" }
        require(text.length <= sessionSettings.maxMessageChars) {
            "The message must be at most ${sessionSettings.maxMessageChars} characters"
        }
        val messages = turnRepository.findBySession(session.id)
            .count { it.mode == AskMode.CHAT && it.status !in NOT_COUNTED }
        if (messages >= sessionSettings.maxMessages) {
            throw AskConversationLimitException(sessionSettings.maxMessages)
        }
        rateLimiter.checkMessage(session.ipHash, AskMode.CHAT)
    }

    /**
     * Answers [text], a recruiter's message in [session], reporting progress to [listener].
     *
     * The session must already be validated (it exists, hasn't expired, belongs to this IP).
     *
     * The refusals of [checkCanAsk] ([IllegalArgumentException], [AskConversationLimitException],
     * [AskRateLimitedException]) come before anything is stored. After that, the turn is stored
     * and ends as one of:
     *
     * @throws AskBudgetExhaustedException if a spending cap is full; the turn is CAPPED
     * @throws AskInputBlockedException if the guardrail refused the message; the turn is BLOCKED
     * @throws GuardrailUnavailableException if the guardrail check failed; the turn is ERROR
     * @throws McpProfileUnavailableException if the profile server failed; the turn is ERROR
     * @throws AskModelUnavailableException if the model call failed; the turn is ERROR
     */
    fun answer(session: AskSession, text: String, listener: AskProgressListener): ChatAnswer {
        checkCanAsk(session, text)
        val turn = TurnInProgress(turnRepository.insertPending(session.id, AskMode.CHAT, text))
        val outcome = try {
            run(turn, session.id, text, listener)
        } catch (ex: Exception) {
            recordFailure(turn, ex)
            throw ex
        }
        return when (outcome) {
            is TurnOutcome.Answered -> ChatAnswer(turnId = turn.id, text = outcome.text, toolTrace = turn.toolTrace)
            is TurnOutcome.Blocked -> throw AskInputBlockedException(outcome.categories)
        }
    }

    /** Everything after the turn is stored. Records the turn itself when it is answered or blocked. */
    private fun run(turn: TurnInProgress, sessionId: UUID, text: String, listener: AskProgressListener): TurnOutcome {
        val context = prepare(sessionId)
        val reservation = budgetService.reserve(
            AskMode.CHAT,
            costCalculator.estimateMax(
                AskMode.CHAT,
                promptChars = chatClient.promptChars(context.system, context.tools),
                inputChars = text.length,
                historyChars = context.historyChars,
            ),
        )
        turn.reservation = reservation

        val guardrail = guardrailService.check(AskMode.CHAT, text)
        turn.guardrail = guardrail
        val verdict = guardrail.verdict
        if (verdict is GuardrailVerdict.Blocked) {
            finish(turn, TurnStatus.BLOCKED, answerText = null)
            countOutcome(turn, reservation.day, blocked = 1)
            logger.info("Chat turn blocked: turnId={} categories={}", turn.id, verdict.categories)
            return TurnOutcome.Blocked(verdict.categories)
        }
        listener.onStep(AskStep.GuardrailPassed)

        val answerText = runToolLoop(turn, context, text, listener)
        finish(turn, TurnStatus.ANSWERED, answerText)
        countOutcome(turn, reservation.day, answers = 1)
        logger.info(
            "Chat turn answered: turnId={} rounds={} toolCalls={} inputTokens={} outputTokens={} " +
                    "cacheReadTokens={} cacheWriteTokens={} costUsd={} latencyMs={}",
            turn.id,
            turn.rounds,
            turn.toolTrace.size,
            turn.tokens.inputTokens,
            turn.tokens.outputTokens,
            turn.tokens.cacheReadTokens,
            turn.tokens.cacheWriteTokens,
            costOf(turn),
            turn.latencyMs(),
        )
        return TurnOutcome.Answered(answerText)
    }

    /** What every model call of this turn sends besides the new message. Reads the profile server. */
    private fun prepare(sessionId: UUID): ChatContext {
        val earlierTurns = turnRepository.findBySession(sessionId)
            .filter { it.mode == AskMode.CHAT && it.status == TurnStatus.ANSWERED && it.answerText != null }
        val history = earlierTurns.flatMap { turn ->
            listOf(BedrockChatClient.userText(turn.userText), BedrockChatClient.assistantText(requireNotNull(turn.answerText)))
        }
        return ChatContext(
            system = "$basePrompt\n\n$PROFILE_INDEX_HEADING\n${profileClient.readIndex()}",
            tools = profileClient.listTools(),
            history = history,
            historyChars = earlierTurns.sumOf { it.userText.length + requireNotNull(it.answerText).length },
        )
    }

    /**
     * Calls the model until it answers, running the tools it asks for in between. Returns the
     * answer text.
     */
    private fun runToolLoop(turn: TurnInProgress, context: ChatContext, text: String, listener: AskProgressListener): String {
        var messages = context.history + BedrockChatClient.userText(text)
        while (true) {
            listener.onStep(AskStep.Composing)
            val reply = chatClient.converse(context.system, messages, context.tools, agent.chatMaxTokens)
            turn.tokens += reply.usage

            if (!reply.wantsTools) return reply.text.ifBlank { NO_ANSWER }
            if (turn.rounds == agent.chatMaxToolRounds) {
                // The last call was told to answer now; it's a request, not a guarantee.
                logger.info("Chat turn still asked for tools after the limit: turnId={}", turn.id)
                return reply.text.ifBlank { NO_ANSWER }
            }

            turn.rounds += 1
            val results = runTools(turn, context, reply.toolRequests, listener)
            val note = if (turn.rounds == agent.chatMaxToolRounds) TOOL_LIMIT_NOTE else null
            messages = messages + reply.message + BedrockChatClient.toolResults(results, note)
        }
    }

    /**
     * Runs the first [AskProperties.Agent.maxReadsPerRound] tool requests of one reply and refuses
     * the rest with an error result. Every request gets a result: Bedrock refuses the next call if
     * one is missing.
     */
    private fun runTools(
        turn: TurnInProgress,
        context: ChatContext,
        requests: List<ModelToolRequest>,
        listener: AskProgressListener,
    ): List<ToolOutcome> {
        val tooMany = "Too many reads in one step; ask for at most ${agent.maxReadsPerRound}."
        val toolNames = context.tools.mapTo(HashSet()) { it.name }
        val ran = requests.take(agent.maxReadsPerRound).map { request -> runTool(turn, request, toolNames, listener) }
        val refused = requests.drop(agent.maxReadsPerRound).map { ToolOutcome(it.toolUseId, tooMany, isError = true) }
        return ran + refused
    }

    /**
     * Runs one tool request. A tool name the server doesn't have still gets its error result from
     * [McpProfileClient.callTool], so the model can correct itself, but no step and no trace entry:
     * it read nothing, and the visitor would see a tool that doesn't exist.
     */
    private fun runTool(
        turn: TurnInProgress,
        request: ModelToolRequest,
        toolNames: Set<String>,
        listener: AskProgressListener,
    ): ToolOutcome {
        val known = request.name in toolNames
        if (known) listener.onStep(AskStep.ToolCalled(request.name, request.arguments))
        val result = profileClient.callTool(request.name, request.arguments)
        // Added at once, so a failure later in the turn still records the tools that ran.
        if (known) turn.toolTrace += ToolCall(tool = request.name, args = request.arguments, isError = result.isError)
        return ToolOutcome(request.toolUseId, result.text, result.isError)
    }

    /** Stores how the turn ended and settles its reservation with the actual cost. */
    private fun finish(turn: TurnInProgress, status: TurnStatus, answerText: String?) {
        turnRepository.complete(turn.id, completionOf(turn, status, answerText, turn.guardrail?.outcome))
        turn.completed = true
        settle(turn)
    }

    /**
     * Adds the turn to the daily counters. The turn is already stored and its cost settled, so a
     * failure here loses only a statistic: it is logged, and the visitor still gets the outcome.
     */
    private fun countOutcome(turn: TurnInProgress, day: LocalDate, answers: Int = 0, blocked: Int = 0) {
        try {
            statsRepository.incrementCounters(day, answers = answers, blocked = blocked)
        } catch (ex: Exception) {
            logger.warn("Daily counters not updated: turnId={} error={}", turn.id, ex.javaClass.simpleName)
        }
    }

    /**
     * Records a turn that failed: CAPPED if the budget was full, ERROR otherwise, with what was
     * spent until then. Problems while recording are attached to [ex], never hide it.
     */
    private fun recordFailure(turn: TurnInProgress, ex: Exception) {
        val status = if (ex is AskBudgetExhaustedException) TurnStatus.CAPPED else TurnStatus.ERROR
        val guardrailOutcome = if (ex is GuardrailUnavailableException) GuardrailOutcome.ERROR else turn.guardrail?.outcome
        if (!turn.completed) {
            try {
                turnRepository.complete(turn.id, completionOf(turn, status, answerText = null, guardrailOutcome))
                turn.completed = true
            } catch (inner: Exception) {
                ex.addSuppressed(inner)
            }
        }
        try {
            settle(turn)
        } catch (inner: Exception) {
            ex.addSuppressed(inner)
        }
        logger.warn(
            "Chat turn failed: turnId={} status={} error={} costUsd={} latencyMs={}",
            turn.id,
            status,
            ex.javaClass.simpleName,
            costOf(turn),
            turn.latencyMs(),
        )
    }

    /** Settles the reservation, if any, exactly once: released if nothing was spent. */
    private fun settle(turn: TurnInProgress) {
        val reservation = turn.reservation ?: return
        turn.reservation = null
        val cost = costOf(turn)
        if (cost.signum() == 0) budgetService.release(reservation) else budgetService.settle(reservation, cost)
    }

    /**
     * The guardrail check plus all model calls so far. A guardrail check that failed is counted as
     * $0: its price (≈ $0.00015) is too small to matter.
     */
    private fun costOf(turn: TurnInProgress): BigDecimal {
        val guardrailCost = turn.guardrail?.let {
            costCalculator.guardrailCost(it.contentFilterTextUnits, it.promptAttackTextUnits)
        } ?: BigDecimal.ZERO
        return guardrailCost + costCalculator.modelCost(turn.tokens)
    }

    private fun completionOf(
        turn: TurnInProgress,
        status: TurnStatus,
        answerText: String?,
        guardrailOutcome: GuardrailOutcome?,
    ) = TurnCompletion(
        status = status,
        answerText = answerText,
        toolTrace = turn.toolTrace,
        guardrailOutcome = guardrailOutcome,
        guardrailScores = turn.guardrail?.scores,
        tokens = turn.tokens,
        guardrailTextUnits = turn.guardrail?.textUnits ?: 0,
        costUsd = costOf(turn),
        latencyMs = turn.latencyMs(),
    )

    /** What one turn has done so far, so a failure can still be recorded with what was spent. */
    private class TurnInProgress(val id: Long) {
        private val startedAt = System.nanoTime()
        var reservation: Reservation? = null
        var guardrail: GuardrailCheckResult? = null
        var tokens: TokenUsage = TokenUsage.NONE
        var toolTrace: List<ToolCall> = emptyList()
        var rounds = 0
        var completed = false

        fun latencyMs(): Int = ((System.nanoTime() - startedAt) / 1_000_000).toInt()
    }

    /**
     * @property system the system prompt with the profile index
     * @property history the earlier messages and answers of the session, oldest first
     * @property historyChars their characters, for the budget estimate
     */
    private class ChatContext(
        val system: String,
        val tools: List<McpTool>,
        val history: List<Message>,
        val historyChars: Int,
    )

    /** How [run] ended, when it didn't throw. */
    private sealed interface TurnOutcome {
        data class Answered(val text: String) : TurnOutcome
        data class Blocked(val categories: List<String>) : TurnOutcome
    }

    companion object {
        /** Turns that don't count toward the conversation limit: the visitor got no answer. */
        private val NOT_COUNTED = setOf(TurnStatus.ERROR, TurnStatus.CAPPED)

        /** Put before the index in the system prompt; the index itself has no header line. */
        internal const val PROFILE_INDEX_HEADING =
            "## Profile index\nOne line per entry: id | type | title | tags | summary"

        /** Added to the tool results of the last round, so the next call answers. */
        internal const val TOOL_LIMIT_NOTE = "Tool limit reached. Answer now, using only what you already have."

        /** The answer when the model gave no text, e.g. its last call still asked for tools. */
        internal const val NO_ANSWER = "I couldn't finish looking that up. Please try rephrasing your question."
    }
}

/**
 * The answer to one chat message, as [ChatService.answer] returns it.
 *
 * @property turnId the stored turn, for the admin view and for log lines
 * @property text the model's answer as plain text (the frontend shows it without markdown)
 * @property toolTrace the profile tools the model used, in order, shown as "How I found this"
 */
data class ChatAnswer(
    val turnId: Long,
    val text: String,
    val toolTrace: List<ToolCall>,
)

/**
 * Thrown when the input guardrail refused a chat message or a job description. The visitor is
 * told the message was blocked, never why.
 *
 * @property categories the guardrail categories that reached their threshold, for the logs
 */
class AskInputBlockedException(
    val categories: List<String>,
) : RuntimeException("The message was blocked by the input guardrail")

/**
 * Thrown when a chat session already has [limit] messages. The visitor must start a new
 * conversation (a new bot check). Mapped to HTTP 429.
 */
class AskConversationLimitException(
    val limit: Int,
) : RuntimeException("This conversation reached its limit of $limit messages")