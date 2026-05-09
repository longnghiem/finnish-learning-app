package me.longng.finnish_learning_backend.event

import me.longng.finnish_learning_backend.persistence.UserTopicStatsRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

/**
 * Kafka consumer that processes [QuizAnswerEvent]s and updates
 * the pre-aggregated `user_topic_stats` table.
 *
 * This consumer is the "write side" of the event-driven analytics pipeline.
 * The progress/dashboard endpoints (TASK 07) read from `user_topic_stats`
 * without needing to aggregate raw data on every request.
 *
 * The consumer is idempotent in practice: even if an event is delivered twice
 * (at-least-once semantics), the counters simply increment an extra time.
 * For a demo app, this level of accuracy is acceptable. In production,
 * you'd add deduplication (e.g., based on a unique event ID).
 */
@Service
class QuizStatsConsumer(
    private val userTopicStatsRepository: UserTopicStatsRepository,
) {
    private val logger = LoggerFactory.getLogger(QuizStatsConsumer::class.java)

    /**
     * Processes a single quiz answer event.
     *
     * Called by Spring Kafka's listener container when a message arrives.
     * Delegates to [UserTopicStatsRepository.upsert] which performs an
     * atomic INSERT … ON CONFLICT DO UPDATE to maintain counters and streaks.
     */
    @KafkaListener(
        topics = [QuizEventProducer.TOPIC_NAME],
        groupId = "\${spring.kafka.consumer.group-id}",
    )
    fun onQuizAnswer(event: QuizAnswerEvent) {
        logger.info(
            "Consuming QuizAnswerEvent: userId={}, cardId={}, topicId={}, correct={}",
            event.userId, event.cardId, event.topicId, event.correct,
        )

        userTopicStatsRepository.upsert(
            userId = event.userId,
            topicId = event.topicId,
            correct = event.correct,
        )

        logger.debug("Updated user_topic_stats for userId={}, topicId={}", event.userId, event.topicId)
    }
}