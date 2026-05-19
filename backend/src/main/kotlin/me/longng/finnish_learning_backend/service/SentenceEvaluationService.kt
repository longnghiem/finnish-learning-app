package me.longng.finnish_learning_backend.service

import me.longng.finnish_learning_backend.controller.dto.EvaluateSentenceResponse
import me.longng.finnish_learning_backend.service.groq.DailyQuotaTracker
import me.longng.finnish_learning_backend.service.groq.GroqClient
import me.longng.finnish_learning_backend.service.groq.SentenceEvaluationQuotaExceededException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Entry point for the sentence-evaluation feature.
 *
 * Responsibilities:
 *  1. Validate the request body at the service boundary.
 *  2. Enforce the per-user daily quota *before* the upstream call, so a
 *     rate-limited user never burns Groq credits.
 *  3. Delegate to [GroqClient] for the actual AI call.
 */
@Service
class SentenceEvaluationService(
    private val groqClient: GroqClient,
    private val quotaTracker: DailyQuotaTracker,
    @Value("\${app.groq.daily-quota}") private val dailyQuota: Int,
) {
    private val logger = LoggerFactory.getLogger(SentenceEvaluationService::class.java)

    /**
     * Evaluates [sentence] on behalf of [userId].
     */
    fun evaluate(userId: Int, sentence: String): EvaluateSentenceResponse {
        require(sentence.isNotBlank()) { "Sentence must not be blank" }

        if (!quotaTracker.tryConsume(userId, dailyQuota)) {
            throw SentenceEvaluationQuotaExceededException(userId, dailyQuota)
        }

        val result = groqClient.evaluate(sentence)

        logger.info(
            "Sentence evaluated: userId={} cefrLevel={} hasGrammarMistake={} hasTypo={} wordUsedCorrectly={} feedback={} correction={}",
            userId, result.cefrLevel, result.hasGrammarMistake, result.hasTypo, result.wordUsedCorrectly, result.feedback, result.correction
        )
        return result
    }
}