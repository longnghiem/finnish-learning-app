package me.longng.finnish_learning_backend.service.groq

import me.longng.finnish_learning_backend.controller.dto.EvaluateSentenceResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue

/**
 * HTTP client for Groq's Groq chat-completions endpoint.
 *
 * The endpoint follows the OpenAI Chat Completions shape, which means the
 * response is a list of `choices`, each carrying a `message` whose `content`
 * is a free-form string. We force that string to be JSON via the
 * `response_format = { type: "json_object" }` field and a strict system prompt.
 *
 * Failure modes:
 *  - Missing API key                         → [SentenceEvaluationMisconfiguredException]
 *  - HTTP error / network failure / timeout  → [SentenceEvaluationUpstreamException]
 *  - Empty `choices` or unparseable JSON     → [SentenceEvaluationUpstreamException]
 */
@Component
class GroqClient(
    private val groqRestClient: RestClient,
    private val objectMapper: ObjectMapper,
    @Value("\${app.groq.api-key}") private val apiKey: String,
    @Value("\${app.groq.model}") private val model: String,
    @Value("classpath:prompts/sentence-evaluation-system-prompt.md")
    private val systemPromptResource: Resource,
) {
    private val logger = LoggerFactory.getLogger(GroqClient::class.java)

    private val systemPrompt: String by lazy {
        systemPromptResource.getContentAsString(Charsets.UTF_8).trim()
    }

    fun evaluate(sentence: String): EvaluateSentenceResponse {
        require(sentence.isNotBlank()) { "sentence must not be blank" }
        if (apiKey.isBlank()) throw SentenceEvaluationMisconfiguredException()

        val requestBody = mapOf(
            "model" to model,
            "response_format" to mapOf("type" to "json_object"),
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to sentence),
            )
        )

        val rawContent = callGroq(requestBody)
        return parseEvaluation(rawContent).normalised()
    }

    // The url "chat/completions" are documented in https://console.groq.com/docs/api-reference#chat
    private fun callGroq(requestBody: Map<String, Any>): String =
        try {
            val response = groqRestClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(ChatCompletionResponse::class.java)
            response?.choices?.firstOrNull()?.message?.content
                ?: throw SentenceEvaluationUpstreamException("Groq returned an empty response")
        } catch (ex: RestClientException) {
            logger.error("Groq HTTP call failed", ex)
            throw SentenceEvaluationUpstreamException("Groq upstream error: ${ex.message}", ex)
        }

    private fun parseEvaluation(content: String): EvaluateSentenceResponse =
        try {
            objectMapper.readValue<EvaluateSentenceResponse>(content)
        } catch (ex: Exception) {
            logger.error("Failed to parse Groq JSON content: {}", content, ex)
            throw SentenceEvaluationUpstreamException("Failed to parse Groq response", ex)
        }

    /** Blank `correction` strings collapse to null so clients can null-check. */
    private fun EvaluateSentenceResponse.normalised(): EvaluateSentenceResponse =
        copy(correction = correction?.takeIf { it.isNotBlank() })

    /**
     * Minimal projection of the OpenAI-compatible response we actually consume.
     * Example response is documented in https://console.groq.com/docs/api-reference#chat
     * */
    private data class ChatCompletionResponse(
        val choices: List<Choice> = emptyList(),
    ) {
        data class Choice(val message: Message = Message())
        data class Message(val content: String = "")
    }

}

/**
 * Thrown when a user has already used their full daily evaluation quota.
 *
 * Mapped to HTTP 429 by `GlobalExceptionHandler`.
 *
 * @property userId      The user that hit the limit (logged, never returned to the client).
 * @property dailyLimit  The configured per-user daily limit, included in the response message.
 */
class SentenceEvaluationQuotaExceededException(
    val userId: Int,
    val dailyLimit: Int,
) : RuntimeException("User $userId exceeded daily evaluation quota of $dailyLimit")

/**
 * Thrown when the upstream AI provider call fails or returns a payload we cannot parse.
 *
 * Mapped to HTTP 502 by `GlobalExceptionHandler`. The original cause (if any) is kept
 * so it surfaces in server logs.
 */
class SentenceEvaluationUpstreamException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Thrown when the Groq API key is not configured on this instance.
 *
 * Treated as a server-side problem (HTTP 502) rather than a client error — the
 * client did nothing wrong, the deployment is incomplete.
 */
class SentenceEvaluationMisconfiguredException(
    message: String = "Groq API key is not configured",
) : RuntimeException(message)