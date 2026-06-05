package me.longng.finnish_learning_backend.service

import me.longng.finnish_learning_backend.controller.dto.QuizCardResponse
import me.longng.finnish_learning_backend.controller.dto.SubmitAnswerResponse
import me.longng.finnish_learning_backend.domain.Card
import me.longng.finnish_learning_backend.domain.ReviewSchedule
import me.longng.finnish_learning_backend.domain.calculateNextReview
import me.longng.finnish_learning_backend.event.QuizAnswerEvent
import me.longng.finnish_learning_backend.event.QuizEventProducer
import me.longng.finnish_learning_backend.persistence.CardRepository
import me.longng.finnish_learning_backend.persistence.ReviewScheduleRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Core quiz business logic — orchestrates card selection and answer processing.
 *
 * This service ties together:
 * - [CardRepository] for card data
 * - [ReviewScheduleRepository] for spaced repetition state
 * - [calculateNextReview] (SM-2 algorithm) for schedule computation
 * - [ApplicationEventPublisher] to emit a [QuizAnswerEvent] that is forwarded to Kafka
 *   only after the transaction commits.
 */
@Service
class QuizService (
    private val cardRepository: CardRepository,
    private val reviewScheduleRepository: ReviewScheduleRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    private val logger = LoggerFactory.getLogger(QuizService::class.java)

    /**
     * Fetches cards for a quiz session in a given topic: Due cards + new cards
     * The combined result is capped at [limit].
     */
    @Transactional(readOnly = true)
    fun getQuizCards(userId: Int, topicId: Int, limit: Int): List<QuizCardResponse> {
        require(limit > 0) { "Limit must be positive, got $limit" }

        val dueSchedules: List<ReviewSchedule> = reviewScheduleRepository.findDueCards(userId, topicId, limit)
        val dueCards: List<Pair<Card, ReviewSchedule>> = dueSchedules.mapNotNull { schedule ->
            cardRepository.findById(schedule.cardId)?.let {card -> card to schedule }
        }

        val remainingSlots = limit - dueCards.size
        val newCards: List<Card> = if (remainingSlots > 0) {
            cardRepository.findNewCardsForUser(userId, topicId, remainingSlots)
        } else {
            emptyList()
        }

        val dueResponses: List<QuizCardResponse> = dueCards.map { (card, schedule) ->
            card.toQuizResponse(isNew = false, schedule = schedule)
        }
        val newResponses: List<QuizCardResponse> = newCards.map { card ->
            card.toQuizResponse(isNew = true, schedule = null)
        }

        return dueResponses + newResponses
    }

    /**
     * Processes a quiz answer: runs SM-2, persists the updated schedule, publishes Kafka event.
     * Publishes the event after commit to ensure no event is published for a rolled-back answer
     *
     * @param quality Self-assessed recall quality (UI sends 1/3/4/5).
     * @return The updated schedule state for frontend display.
     * @throws IllegalArgumentException if [quality] is not in 0..5.
     * @throws CardNotFoundException if [cardId] doesn't exist.
     */
    @Transactional
    fun submitAnswer(userId: Int, cardId: Int, quality: Int): SubmitAnswerResponse {
        require(quality in 0..5) { "Quality must be between 0 and 5, got $quality" }
        val card = cardRepository.findById(cardId) ?: throw CardNotFoundException(cardId)

        // Update review schedule
        val currentReviewSchedule = reviewScheduleRepository.findByUserAndCard(userId, cardId)
        val nextReview = calculateNextReview(
            quality = quality,
            currentRepetition = currentReviewSchedule?.repetition ?: 0,
            currentEaseFactor = currentReviewSchedule?.easeFactor ?: 2.5,
            currentIntervalDays = currentReviewSchedule?.intervalDays ?: 0,
        )
        reviewScheduleRepository.upsert(
            userId = userId,
            cardId = cardId,
            repetition = nextReview.repetition,
            easeFactor = nextReview.easeFactor,
            intervalDays = nextReview.intervalDays,
            nextReviewDate = nextReview.nextReviewDate,
        )

        // Emit an in-process Spring application event. QuizAnswerEventListener forwards it to Kafka
        // on TransactionPhase.AFTER_COMMIT
        val correct = quality >= 3
        applicationEventPublisher.publishEvent(QuizAnswerEvent(
            userId = userId,
            cardId = cardId,
            topicId = card.topicId,
            quality = quality,
            correct = correct,
            timestamp = Instant.now(),
        ))

        logger.info(
            "Quiz answer submitted: userId={}, cardId={}, quality={}, correct={}, nextReview={}",
            userId, cardId, quality, correct, nextReview.nextReviewDate,
        )

        return SubmitAnswerResponse(
            cardId = cardId,
            repetition = nextReview.repetition,
            easeFactor = nextReview.easeFactor,
            intervalDays = nextReview.intervalDays,
            nextReviewDate = nextReview.nextReviewDate,
            correct = correct,
        )
    }

    private fun Card.toQuizResponse(
        isNew: Boolean,
        schedule: ReviewSchedule?,
    ): QuizCardResponse = QuizCardResponse(
        cardId = id,
        topicId = topicId,
        name = name,
        exampleSentence = exampleSentence,
        translation = translation,
        imageUrl = "/api/images/$imageFilename",
        isNew = isNew,
        repetition = schedule?.repetition,
        nextReviewDate = schedule?.nextReviewDate,
    )
}