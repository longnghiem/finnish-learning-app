package me.longng.finnish_learning_backend.service.bedrock

import me.longng.finnish_learning_backend.controller.dto.EvaluateEssayResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration
import software.amazon.awssdk.services.bedrockruntime.model.JsonSchemaDefinition
import software.amazon.awssdk.services.bedrockruntime.model.Message
import software.amazon.awssdk.services.bedrockruntime.model.OutputConfig
import software.amazon.awssdk.services.bedrockruntime.model.OutputFormat
import software.amazon.awssdk.services.bedrockruntime.model.OutputFormatStructure
import software.amazon.awssdk.services.bedrockruntime.model.OutputFormatType
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock
import software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue

@Component
class BedrockEssayClient(
    private val bedrockRuntimeClient: BedrockRuntimeClient,
    private val objectMapper: ObjectMapper,
    @Value("\${app.bedrock.model-id}") private val modelId: String,
    @Value("\${app.bedrock.max-tokens}") private val maxTokens: Int,
    @Value("classpath:prompts/essay-evaluation-system-prompt.md")
    private val systemPromptResource: Resource,
) {
    private val logger = LoggerFactory.getLogger(BedrockEssayClient::class.java)

    private val systemPrompt: String by lazy {
        systemPromptResource.getContentAsString(Charsets.UTF_8).trim()
    }

    fun evaluate(promptTitle: String, essay: String): EvaluateEssayResponse {
        require(promptTitle.isNotBlank()) { "promptTitle must not be blank" }
        require(essay.isNotBlank()) { "essay must not be blank" }

        val request = buildRequest(promptTitle, essay)
        val response = callBedrock(request)

        return parseEvaluation(extractText(response))
    }

    private fun buildRequest(promptTitle: String, essay: String): ConverseRequest {
        val userContent = """
            Essay prompt:
            $promptTitle

            The learner's essay:
            ---
            $essay
            ---
        """.trimIndent()

        return ConverseRequest.builder()
            .modelId(modelId)
            .system(SystemContentBlock.fromText(systemPrompt))
            .messages(
                Message.builder()
                    .role(ConversationRole.USER)
                    .content(ContentBlock.fromText(userContent))
                    .build(),
            )
            .inferenceConfig(
                InferenceConfiguration.builder()
                    .maxTokens(maxTokens)
                    .build(),
            )
            .outputConfig(
                OutputConfig.builder()
                    .textFormat(
                        OutputFormat.builder()
                            .type(OutputFormatType.JSON_SCHEMA)
                            .structure(
                                OutputFormatStructure.fromJsonSchema(
                                    JsonSchemaDefinition.builder()
                                        .name("essay_evaluation")
                                        .description(
                                            "CEFR grade, topic relevance and concrete " +
                                                    "issues for one Finnish essay",
                                        )
                                        .schema(ESSAY_RESPONSE_SCHEMA)
                                        .build(),
                                ),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()
    }

    private fun callBedrock(request: ConverseRequest): ConverseResponse {
        val startedAt = System.nanoTime()
        try {
            val response = bedrockRuntimeClient.converse(request)
            logger.info(
                "Bedrock converse ok: model={} latencyMs={} inputTokens={} outputTokens={}",
                modelId,
                (System.nanoTime() - startedAt) / 1_000_000,
                response.usage()?.inputTokens(),
                response.usage()?.outputTokens(),
            )
            return response
        } catch (ex: AccessDeniedException) {
            logger.error("Bedrock denied access to model {}", modelId, ex)
            throw EssayEvaluationMisconfiguredException(
                "Bedrock denied access to model '$modelId'. Two likely causes: model access " +
                        "is not enabled for this account in this region (Bedrock console → Model " +
                        "access), or the IAM role is missing bedrock:InvokeModel / is not attached " +
                        "to this instance.",
                ex,
            )
        } catch (ex: SdkException) {
            logger.error("Bedrock converse call failed: model={}", modelId, ex)
            throw EssayEvaluationUpstreamException("Bedrock upstream error: ${ex.message}", ex)
        }
    }

    /**
     * Pulls the single text block out of the Converse response.
     */
    private fun extractText(response: ConverseResponse): String =
        response.output()?.message()?.content()
            ?.firstNotNullOfOrNull { block -> block.text()?.takeIf(String::isNotBlank) }
            ?: run {
                logger.error(
                    "Bedrock returned no text content: model={} stopReason={}",
                    modelId,
                    response.stopReasonAsString(),
                )
                throw EssayEvaluationUpstreamException("Bedrock returned an empty response")
            }

    /**
     * Deserialises the schema-constrained JSON body.
     */
    private fun parseEvaluation(json: String): EvaluateEssayResponse =
        try {
            objectMapper.readValue<EvaluateEssayResponse>(json)
        } catch (ex: JacksonException) {
            logger.error("Failed to deserialise Bedrock response into EvaluateEssayResponse", ex)
            throw EssayEvaluationUpstreamException("Failed to parse Bedrock response", ex)
        }

    companion object {
        /**
         * Response contract sent to Bedrock as `outputConfig.textFormat.structure.jsonSchema`.
         *
         * This schema, `EvaluateEssayResponse` (`controller/dto/EvaluateEssayResponse.kt`) and
         * the TypeScript types on frontend are one contract written three times, with no
         * compiler link between them. Change one, change all three.
         *
         * In particular the six `cefrLevel` values must stay identical to the six
         * `@JsonProperty` values on `EssayCefrLevel`, and the two `kind` values to
         * `EssayIssueKind`'s constants.
         *
         */
        internal const val ESSAY_RESPONSE_SCHEMA: String = """
        {
          "type": "object",
          "additionalProperties": false,
          "required": ["cefrLevel", "onTopic", "issues"],
          "properties": {
            "cefrLevel": {
              "type": "string",
              "description": "CEFR sub-level the essay as a whole is graded at.",
              "enum": ["A1.1", "A1.2", "A2.1", "A2.2", "B1.1", "B1.2"]
            },
            "onTopic": {
              "type": "boolean",
              "description": "True only if the essay addresses the given prompt AND is written in Finnish. An essay in any other language is always false."
            },
            "issues": {
              "type": "array",
              "description": "Concrete grammar mistakes and typos found in the essay. May be empty.",
              "items": {
                "type": "object",
                "additionalProperties": false,
                "required": ["kind", "original", "suggestion"],
                "properties": {
                  "kind": {
                    "type": "string",
                    "description": "Whether this issue is a grammar mistake or a typo.",
                    "enum": ["GRAMMAR", "TYPO"]
                  },
                  "original": {
                    "type": "string",
                    "description": "The offending fragment copied character-for-character from the learner's essay, exactly as they wrote it - not corrected, not shortened, not re-punctuated. The application locates this fragment in the essay by exact string match, so any edit here silently breaks it. Keep it to a word or a clause."
                  },
                  "suggestion": {
                    "type": "string",
                    "description": "The corrected form of that same fragment: Finnish text that can be substituted directly for the original in the essay, and nothing else. Not an explanation of the mistake, not a rewrite of the whole sentence."
                  }
                }
              }
            },
            "feedback": {
              "type": "string",
              "description": "2-5 sentences of holistic feedback in English: structure, cohesion, register, task fulfilment. Omit if there is nothing to add beyond the issues."
            }
          }
        }
        """
    }

}

/**
 * Thrown when a user has already used their full daily essay-evaluation quota.
 * Mapped to HTTP 429 by `GlobalExceptionHandler`.
 *
 */
class EssayEvaluationQuotaExceededException(
    val userId: Int,
    val dailyLimit: Int,
) : RuntimeException("User $userId exceeded daily essay evaluation quota of $dailyLimit")

/**
 * Thrown when the Bedrock call fails or returns a payload we cannot parse.
 * Mapped to HTTP 502 by `GlobalExceptionHandler`. The original cause is kept so the SDK's
 * own diagnostics survive into the server log.
 */
class EssayEvaluationUpstreamException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Thrown when Bedrock rejects the call for reasons the deployment controls: credentials that
 * do not resolve, model access that was never enabled, or an IAM role without `bedrock:InvokeModel`.
 *
 * Treated as a server-side problem (HTTP 502) rather than a client error — the client did
 * nothing wrong, the deployment is incomplete.
 */
class EssayEvaluationMisconfiguredException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)