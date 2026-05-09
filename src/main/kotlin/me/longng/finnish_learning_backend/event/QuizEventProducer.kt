package me.longng.finnish_learning_backend.event

import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

/**
 * Publishes [QuizAnswerEvent]s to the `quiz-answers` Kafka topic.
 *
 * This is called by the quiz service after the synchronous DB update to`review_schedules` completes.
 * The event triggers the asynchronous stats aggregation pipeline.
 *
 * Publishing is fire-and-forget: if Kafka is unavailable, the quiz answer is still persisted in the DB (source of truth).
 * The stats update will be lost, but this is acceptable for a non-critical analytics path.
 */
@Service
class QuizEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, QuizAnswerEvent>,
) {
    private val logger = LoggerFactory.getLogger(QuizEventProducer::class.java)

    /**
     * Publishes a quiz answer event to the Kafka topic.
     *
     * The message key is `"userId-cardId"` to ensure all answers for the same
     * user-card pair are routed to the same partition (preserving per-pair ordering).
     *
     * Uses [whenComplete] (non-blocking) rather than `.get()` (blocking) because
     * the REST response should not wait for Kafka acknowledgement.
     */
    fun publish(event: QuizAnswerEvent) {
        val key = "${event.userId}-${event.cardId}"
        kafkaTemplate.send(TOPIC_NAME, key, event)
            .whenComplete { result, ex ->
                if (ex != null) {
                    logger.error("Failed to publish QuizAnswerEvent: {}", event, ex)
                } else {
                    logger.debug(
                        "Published QuizAnswerEvent to partition={}, offset={}",
                        result.recordMetadata.partition(),
                        result.recordMetadata.offset(),
                    )
                }
            }
    }

    companion object {
        /** The Kafka topic name for quiz answer events. */
        const val TOPIC_NAME = "quiz-answers"
    }
}