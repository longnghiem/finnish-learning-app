package me.longng.finnish_learning_backend.service

import me.longng.finnish_learning_backend.controller.dto.EssayPromptResponse
import me.longng.finnish_learning_backend.controller.dto.EvaluateEssayResponse
import me.longng.finnish_learning_backend.controller.dto.MAX_ESSAY_LENGTH
import me.longng.finnish_learning_backend.controller.dto.MIN_ESSAY_LENGTH
import me.longng.finnish_learning_backend.persistence.EssayTopicRepository
import me.longng.finnish_learning_backend.service.bedrock.BedrockEssayClient
import me.longng.finnish_learning_backend.service.bedrock.EssayEvaluationQuotaExceededException
import me.longng.finnish_learning_backend.service.bedrock.EssayQuotaTracker
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Entry point for the essay-evaluation feature.
 *
 * Responsibilities, in the order [evaluate] performs them:
 *  1. Validate the essay at the service boundary — non-blank, within the accepted length band.
 *  2. Resolve the chosen prompt. An unknown `promptId` is a client error and is rejected
 *     *before* the quota is touched: a 400 the caller caused must never cost them a quota unit.
 *  3. Enforce the per-user daily quota *before* the upstream call, so a rate-limited user never
 *     triggers a billable Bedrock request.
 *  4. Delegate to [BedrockEssayClient], passing the prompt title the model grades relevance against.
 */
@Service
class EssayEvaluationService(
    private val bedrockEssayClient: BedrockEssayClient,
    private val quotaTracker: EssayQuotaTracker,
    private val essayTopicRepository: EssayTopicRepository,
    @Value("\${app.bedrock.daily-quota}") private val dailyQuota: Int,
) {
    private val logger = LoggerFactory.getLogger(EssayEvaluationService::class.java)

    /**
     * Returns the selectable essay prompts for [topicId], ordered by id.
     */
    fun getPrompts(topicId: Int): List<EssayPromptResponse> =
        essayTopicRepository.findByTopicId(topicId)
            .map { EssayPromptResponse(id = it.id, title = it.title) }

    /**
     * Evaluates [essay], written by [userId] against the prompt identified by [promptId].
     *
     * @throws IllegalArgumentException if the essay is blank, outside the accepted length band,
     *         or [promptId] does not exist — all mapped to HTTP 400.
     * @throws EssayEvaluationQuotaExceededException if the user's daily budget is spent (HTTP 429).
     */
    fun evaluate(userId: Int, promptId: Int, essay: String): EvaluateEssayResponse {
        require(essay.isNotBlank()) { "Essay must not be blank" }
        require(essay.length in MIN_ESSAY_LENGTH..MAX_ESSAY_LENGTH) {
            "Essay must be between $MIN_ESSAY_LENGTH and $MAX_ESSAY_LENGTH characters, " +
                    "got ${essay.length}"
        }

        val prompt = requireNotNull(essayTopicRepository.findById(promptId)) {
            "Unknown essay prompt id: $promptId"
        }

        if (!quotaTracker.tryConsume(userId, dailyQuota)) {
            throw EssayEvaluationQuotaExceededException(userId, dailyQuota)
        }

        val result = bedrockEssayClient.evaluate(prompt.title, essay)

        logger.info(
            "Essay evaluated: userId={} promptId={} cefrLevel={} onTopic={} issueCount={}",
            userId, promptId, result.cefrLevel, result.onTopic, result.issues.size,
        )
        return result
    }
}