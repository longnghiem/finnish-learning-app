package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.config.AskProperties
import me.longng.finnish_learning_backend.domain.ask.AskMode
import me.longng.finnish_learning_backend.domain.ask.AskSession
import me.longng.finnish_learning_backend.domain.ask.ToolCall
import me.longng.finnish_learning_backend.domain.ask.TurnStatus
import me.longng.finnish_learning_backend.persistence.AskTurnRepository
import me.longng.finnish_learning_backend.service.ask.mcp.McpProfileClient
import me.longng.finnish_learning_backend.service.ask.mcp.McpProfileUnavailableException
import me.longng.finnish_learning_backend.service.ask.mcp.McpTool
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.bedrockruntime.model.Message
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
 * service runs at most [AskProperties.Agent.maxReadsPerRound] of them ([ProfileToolRunner], which
 * reads through [McpProfileClient]; any extra request gets an error result), sends all results
 * back in one message and calls the model again. One such ask-and-read step is a round. After
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
 * settled with what was really spent, also when something fails halfway ([AskTurnRecorder]).
 * Logs carry the turn id, counts, tokens, cost and latency, never the message or the answer.
 */
@Service
class ChatService(
    private val turnRepository: AskTurnRepository,
    private val recorder: AskTurnRecorder,
    private val rateLimiter: AskRateLimiter,
    private val costCalculator: CostCalculator,
    private val guardrailService: GuardrailService,
    private val profileClient: McpProfileClient,
    private val toolRunner: ProfileToolRunner,
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
        val turn = recorder.start(session.id, AskMode.CHAT, text)
        val outcome = try {
            run(turn, session.id, text, listener)
        } catch (ex: Exception) {
            recorder.recordFailure(turn, ex)
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
        val reservation = recorder.reserve(
            turn,
            costCalculator.estimateMax(
                AskMode.CHAT,
                promptChars = chatClient.promptChars(context.system, context.tools),
                inputChars = text.length,
                historyChars = context.historyChars,
            ),
        )

        val guardrail = guardrailService.check(AskMode.CHAT, text)
        turn.guardrail = guardrail
        val verdict = guardrail.verdict
        if (verdict is GuardrailVerdict.Blocked) {
            recorder.finish(turn, TurnStatus.BLOCKED)
            recorder.countOutcome(turn, reservation.day, blocked = 1)
            logger.info("Chat turn blocked: turnId={} categories={}", turn.id, verdict.categories)
            return TurnOutcome.Blocked(verdict.categories)
        }
        listener.onStep(AskStep.GuardrailPassed)

        val answerText = runToolLoop(turn, context, text, listener)
        recorder.finish(turn, TurnStatus.ANSWERED, answerText = answerText)
        recorder.countOutcome(turn, reservation.day, answers = 1)
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
            recorder.costOf(turn),
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
            val results = toolRunner.runRound(turn, reply.toolRequests, context.tools, listener)
            val note = if (turn.rounds == agent.chatMaxToolRounds) TOOL_LIMIT_NOTE else null
            messages = messages + reply.message + BedrockChatClient.toolResults(results, note)
        }
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