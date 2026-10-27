package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.config.AskProperties
import me.longng.finnish_learning_backend.domain.ask.TokenUsage
import me.longng.finnish_learning_backend.service.ask.mcp.McpTool
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.core.document.Document
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException
import software.amazon.awssdk.services.bedrockruntime.model.AutoToolChoice
import software.amazon.awssdk.services.bedrockruntime.model.CachePointBlock
import software.amazon.awssdk.services.bedrockruntime.model.CachePointType
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration
import software.amazon.awssdk.services.bedrockruntime.model.Message
import software.amazon.awssdk.services.bedrockruntime.model.ResourceNotFoundException
import software.amazon.awssdk.services.bedrockruntime.model.StopReason
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock
import software.amazon.awssdk.services.bedrockruntime.model.Tool
import software.amazon.awssdk.services.bedrockruntime.model.ToolChoice
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultStatus
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper
import java.time.Duration

/**
 * Makes one Converse call to the chat model ([AskProperties.Model]) and translates between
 * Bedrock's types and the chatbot's. The tool loop around it is [ChatService]'s job.
 *
 * Every request is built the same way:
 * - the system prompt as one text block, without a cache point;
 * - the messages, with one cache point added at the end of the latest message. Bedrock then
 *   caches everything up to that point, and the next call finds the cached part again although
 *   the point has moved on. Haiku 4.5 only caches a prefix of at least 4,096 tokens and ignores a
 *   cache point below that without an error, so short conversations simply aren't cached;
 * - the [McpTool]s as tool specifications with automatic tool choice, on every call: once the
 *   messages contain tool requests and results, Bedrock refuses a request without them;
 * - `additionalModelRequestFields` only if [AskProperties.Model.additionalRequestFields] is set.
 *   No thinking field: Haiku 4.5 doesn't think unless asked to.
 *
 * Each call may take [AskProperties.Model.timeoutMs], the SDK's own retries included. The SDK
 * retries throttling and server errors by itself; that is kept on purpose (unlike for the
 * guardrail), because a retried model call costs nothing when the first try failed.
 *
 * Logs the model, the stop reason, the token counts and the latency. Never the messages.
 */
@Component
class BedrockChatClient(
    private val bedrockRuntimeClient: BedrockRuntimeClient,
    private val objectMapper: ObjectMapper,
    properties: AskProperties,
) {
    private val logger = LoggerFactory.getLogger(BedrockChatClient::class.java)

    private val settings = properties.model

    /** Parsed once, so a broken setting stops the app at startup instead of failing every call. */
    private val additionalRequestFields: Document? = parseAdditionalRequestFields(settings.additionalRequestFields)

    /**
     * Sends [messages] to the model, with [system] as the system prompt and [tools] on offer.
     *
     * @param messages the conversation so far, oldest first, starting and ending with a user message
     * @param maxTokens the longest reply allowed
     * @throws AskModelUnavailableException if Bedrock can't be reached, refuses the call, or takes
     *   longer than [AskProperties.Model.timeoutMs]
     */
    fun converse(system: String, messages: List<Message>, tools: List<McpTool>, maxTokens: Int): ChatModelReply {
        require(system.isNotBlank()) { "system must not be blank" }
        require(messages.isNotEmpty()) { "messages must not be empty" }
        require(maxTokens > 0) { "maxTokens must be positive" }

        val startedAt = System.nanoTime()
        val response = call(buildRequest(system, messages, tools, maxTokens))
        val reply = toReply(response)
        logger.info(
            "Chat model call: model={} stopReason={} toolRequests={} inputTokens={} outputTokens={} " +
                    "cacheReadTokens={} cacheWriteTokens={} latencyMs={}",
            settings.id,
            reply.stopReason,
            reply.toolRequests.size,
            reply.usage.inputTokens,
            reply.usage.outputTokens,
            reply.usage.cacheReadTokens,
            reply.usage.cacheWriteTokens,
            (System.nanoTime() - startedAt) / 1_000_000,
        )
        return reply
    }

    /**
     * The characters of what every call sends besides the conversation: [system] plus the tool
     * specifications. [CostCalculator.estimateMax] turns this into tokens for the budget reserve.
     */
    fun promptChars(system: String, tools: List<McpTool>): Int =
        system.length + tools.sumOf { tool ->
            tool.name.length + tool.description.length + objectMapper.writeValueAsString(tool.inputSchema).length
        }

    private fun buildRequest(system: String, messages: List<Message>, tools: List<McpTool>, maxTokens: Int): ConverseRequest =
        ConverseRequest.builder()
            .modelId(settings.id)
            .system(SystemContentBlock.fromText(system))
            .messages(withCachePointAtEnd(messages))
            .apply { if (tools.isNotEmpty()) toolConfig(toolConfiguration(tools)) }
            .inferenceConfig(InferenceConfiguration.builder().maxTokens(maxTokens).build())
            .apply { if (additionalRequestFields != null) additionalModelRequestFields(additionalRequestFields) }
            .overrideConfiguration { config -> config.apiCallTimeout(Duration.ofMillis(settings.timeoutMs)) }
            .build()

    private fun call(request: ConverseRequest): ConverseResponse =
        try {
            bedrockRuntimeClient.converse(request)
        } catch (ex: AccessDeniedException) {
            logger.error("Bedrock denied the chat model: model={} requestId={}", settings.id, ex.requestId())
            throw AskModelUnavailableException(
                "Bedrock denied access to the chat model '${settings.id}'. In production, ASK_MODEL_ID " +
                        "was probably changed to a model the instance policy doesn't allow; locally, " +
                        "the IAM user may lack access to it. See docs/runbooks/recruiter-chatbot-aws-setup.md, " +
                        "Part B (model access).",
                ex,
            )
        } catch (ex: ResourceNotFoundException) {
            logger.error("Bedrock didn't find the chat model: model={} requestId={}", settings.id, ex.requestId())
            throw AskModelUnavailableException(
                "Bedrock didn't find the chat model '${settings.id}', or it isn't enabled for this account. " +
                        "Check ASK_MODEL_ID (an inference profile id such as eu.anthropic...), and for an " +
                        "Anthropic model that the account has submitted Anthropic's use case form. See " +
                        "docs/runbooks/recruiter-chatbot-aws-setup.md, Part B (model access).",
                ex,
            )
        } catch (ex: AwsServiceException) {
            // Only the error code and the request id: the message may quote parts of the request.
            logger.error(
                "Chat model call refused: model={} status={} errorCode={} requestId={}",
                settings.id,
                ex.statusCode(),
                ex.awsErrorDetails()?.errorCode(),
                ex.requestId(),
            )
            throw AskModelUnavailableException("Bedrock refused the chat model call (HTTP ${ex.statusCode()})", ex)
        } catch (ex: SdkException) {
            logger.error("Chat model call failed: model={} error={}", settings.id, ex.javaClass.simpleName)
            throw AskModelUnavailableException("The chat model couldn't be reached: ${ex.javaClass.simpleName}", ex)
        }

    private fun toReply(response: ConverseResponse): ChatModelReply {
        val message = response.output()?.message()
            ?: throw AskModelUnavailableException("Bedrock's answer has no message")
        val content = message.content().orEmpty()
        return ChatModelReply(
            message = message,
            text = extractText(content),
            toolRequests = content.mapNotNull { block -> block.toolUse() }.map { toolUse ->
                ModelToolRequest(
                    toolUseId = toolUse.toolUseId(),
                    name = toolUse.name(),
                    arguments = toolUse.input()?.takeIf { it.isMap }?.toPlainValue().asArguments(),
                )
            },
            stopReason = response.stopReason() ?: StopReason.UNKNOWN_TO_SDK_VERSION,
            usage = response.usage()?.let { usage ->
                TokenUsage(
                    inputTokens = usage.inputTokens() ?: 0,
                    outputTokens = usage.outputTokens() ?: 0,
                    cacheReadTokens = usage.cacheReadInputTokens() ?: 0,
                    cacheWriteTokens = usage.cacheWriteInputTokens() ?: 0,
                )
            } ?: TokenUsage.NONE,
        )
    }

    private fun parseAdditionalRequestFields(json: String): Document? {
        if (json.isBlank()) return null
        val value = try {
            objectMapper.readValue(json, Any::class.java)
        } catch (ex: JacksonException) {
            throw IllegalArgumentException("app.ask.model.additional-request-fields is not valid JSON", ex)
        }
        require(value is Map<*, *>) { "app.ask.model.additional-request-fields must be a JSON object, like {\"thinking\":{...}}" }
        return value.toDocument()
    }

    companion object {
        /** The cache point added at the end of the latest message on every call. */
        private val CACHE_POINT: ContentBlock =
            ContentBlock.fromCachePoint(CachePointBlock.builder().type(CachePointType.DEFAULT).build())

        /** Sent instead of an empty tool result: Bedrock refuses empty text blocks. */
        private const val EMPTY_RESULT = "(empty result)"

        /** A user message with one text block. */
        fun userText(text: String): Message = textMessage(ConversationRole.USER, text)

        /** An assistant message with one text block: an earlier answer, rebuilt from the database. */
        fun assistantText(text: String): Message = textMessage(ConversationRole.ASSISTANT, text)

        /**
         * The user message that answers the model's tool requests: one tool result per request, in
         * the order of [results], each matched to its request by [ToolOutcome.toolUseId]. Bedrock
         * wants all results of one reply in one message.
         *
         * @param note an extra text block after the results, e.g. the "answer now" instruction of
         *   the last call
         */
        fun toolResults(results: List<ToolOutcome>, note: String? = null): Message {
            require(results.isNotEmpty()) { "results must not be empty" }
            val resultBlocks = results.map { result ->
                ContentBlock.fromToolResult(
                    ToolResultBlock.builder()
                        .toolUseId(result.toolUseId)
                        .content(ToolResultContentBlock.fromText(result.text.ifBlank { EMPTY_RESULT }))
                        .status(if (result.isError) ToolResultStatus.ERROR else ToolResultStatus.SUCCESS)
                        .build(),
                )
            }
            val noteBlocks = listOfNotNull(note?.let { ContentBlock.fromText(it) })
            return Message.builder().role(ConversationRole.USER).content(resultBlocks + noteBlocks).build()
        }

        /**
         * The MCP tool as a Bedrock tool specification. The schema's top-level `$schema` line is
         * removed: it only names the JSON Schema version, means nothing to the model, and Bedrock
         * was never tested with it.
         */
        internal fun toolSpecification(tool: McpTool): ToolSpecification =
            ToolSpecification.builder()
                .name(tool.name)
                .apply { if (tool.description.isNotBlank()) description(tool.description) }
                .inputSchema(ToolInputSchema.fromJson((tool.inputSchema - JSON_SCHEMA_KEY).toDocument()))
                .build()

        /**
         * The model's answer text: every text block, joined. A reply may hold no text at all (only
         * tool requests), so the blocks are searched by kind, never taken by position.
         */
        internal fun extractText(content: List<ContentBlock>): String =
            content.mapNotNull { it.text() }.filter { it.isNotBlank() }.joinToString("\n\n").trim()

        private const val JSON_SCHEMA_KEY = "\$schema"

        private fun textMessage(role: ConversationRole, text: String): Message {
            require(text.isNotBlank()) { "A message text must not be blank" }
            return Message.builder().role(role).content(ContentBlock.fromText(text)).build()
        }

        private fun withCachePointAtEnd(messages: List<Message>): List<Message> {
            val latest = messages.last()
            return messages.dropLast(1) + latest.toBuilder().content(latest.content() + CACHE_POINT).build()
        }

        private fun toolConfiguration(tools: List<McpTool>): ToolConfiguration =
            ToolConfiguration.builder()
                .tools(tools.map { Tool.fromToolSpec(toolSpecification(it)) })
                .toolChoice(ToolChoice.fromAuto(AutoToolChoice.builder().build()))
                .build()

        @Suppress("UNCHECKED_CAST")
        private fun Any?.asArguments(): Map<String, Any?> = (this as? Map<String, Any?>).orEmpty()
    }
}

/**
 * One reply of the chat model.
 *
 * @property message the reply exactly as Bedrock sent it. It goes back into the conversation
 *   unchanged when the model asked for tools, so Bedrock can match its tool requests to the results
 * @property text the reply's text blocks joined; empty if the reply has only tool requests
 * @property toolRequests the tools the model asked for, in its order
 * @property usage the tokens of this one call
 */
data class ChatModelReply(
    val message: Message,
    val text: String,
    val toolRequests: List<ModelToolRequest>,
    val stopReason: StopReason,
    val usage: TokenUsage,
) {
    /** True if the model stopped to wait for tool results. */
    val wantsTools: Boolean get() = stopReason == StopReason.TOOL_USE && toolRequests.isNotEmpty()
}

/**
 * One tool the model asked for.
 *
 * @property toolUseId Bedrock's id for the request; its result must carry the same id
 * @property arguments the tool's input, as plain Kotlin values (see [toPlainValue])
 */
data class ModelToolRequest(
    val toolUseId: String,
    val name: String,
    val arguments: Map<String, Any?>,
)

/**
 * The result to send back for one [ModelToolRequest].
 *
 * @property isError true if the tool refused the call; the model sees it as an error and can
 *   correct itself
 */
data class ToolOutcome(
    val toolUseId: String,
    val text: String,
    val isError: Boolean,
)

/**
 * Thrown when the chat model can't answer: Bedrock unreachable, too slow, throttling after the
 * SDK's retries, refusing the request, not finding the model, or denying access to it. The visitor sees an
 * "upstream" error; the message is for the server log and the developer.
 */
class AskModelUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)