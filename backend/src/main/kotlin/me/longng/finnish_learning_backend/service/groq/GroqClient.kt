package me.longng.finnish_learning_backend.service.groq

import me.longng.finnish_learning_backend.controller.dto.EvaluateSentenceResponse
import me.longng.finnish_learning_backend.controller.dto.FinnishLevel
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
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
) {
    private val logger = LoggerFactory.getLogger(GroqClient::class.java)

    fun evaluate(sentence: String): EvaluateSentenceResponse {
        require(sentence.isNotBlank()) { "sentence must not be blank" }
        if (apiKey.isBlank()) throw SentenceEvaluationMisconfiguredException()

        val requestBody = mapOf(
            "model" to model,
            "response_format" to mapOf("type" to "json_object"),
            "messages" to listOf(
                mapOf("role" to "system", "content" to SYSTEM_PROMPT),
                mapOf("role" to "user", "content" to sentence),
            )
        )

        val rawContent = callGroq(requestBody)
        val raw = parseRawEvaluation(rawContent)
        return raw.toResponseEnforcingInvariants()
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

    private fun parseRawEvaluation(content: String): RawEvaluation =
        try {
            objectMapper.readValue<RawEvaluation>(content)
        } catch (ex: Exception) {
            logger.error("Failed to parse Groq JSON content: {}", content, ex)
            throw SentenceEvaluationUpstreamException("Failed to parse Groq response", ex)
        }

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

    /**
     * Internal shape of the JSON the AI is instructed to produce. Field names
     * match [EvaluateSentenceResponse]; conversion adds the invariant enforcement
     * so the public response is trustworthy without further client-side checks.
     */
    private data class RawEvaluation(
        val hasGrammarMistake: Boolean,
        val hasTypo: Boolean,
        val level: FinnishLevel,
        val correction: String? = null,
        val b1Example: String? = null,
    ) {
        fun toResponseEnforcingInvariants(): EvaluateSentenceResponse {
            val needsCorrection = hasGrammarMistake || hasTypo
            val needsB1Example = level == FinnishLevel.A1 || level == FinnishLevel.A2
            return EvaluateSentenceResponse(
                hasGrammarMistake = hasGrammarMistake,
                hasTypo = hasTypo,
                level = level,
                correction = correction?.takeIf { it.isNotBlank() && needsCorrection },
                b1Example = b1Example?.takeIf { it.isNotBlank() && needsB1Example },
            )
        }
    }

    companion object {
        /**
         * System prompt instructing Groq to return a strict JSON object that
         * deserialises directly into [RawEvaluation].
         */
        private val SYSTEM_PROMPT = """
            You evaluate a single Finnish sentence supplied by a learner.

            Respond with a JSON object ONLY — no prose, no Markdown, no code fences —
            containing exactly these fields:

              - hasGrammarMistake (boolean)
                  true if the sentence contains at least one grammatical mistake, false otherwise.
              - hasTypo (boolean)
                  true if the sentence contains at least one typo, false otherwise.
              - level (string)
                  One of "A1", "A2", "B1", "B2", "C1", "C2" (CEFR).
              - correction (string)
                  Required only when grammarCorrect is false OR hasTypo is true.
                  When required, provide the corrected sentence in Finnish.
                  Otherwise return an empty string.
              - b1Example (string)
                  Required only when level is "A1" or "A2".
                  When required, provide a natural B1-level Finnish sentence that
                  uses the most prominent content word from the user's sentence.
                  Otherwise return an empty string.

            All explanatory text in the JSON must be in English, except for the
            Finnish sentences in `correction` and `b1Example` which must be in Finnish.
        """.trimIndent()
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