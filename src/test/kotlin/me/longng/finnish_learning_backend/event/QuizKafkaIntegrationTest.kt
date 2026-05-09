package me.longng.finnish_learning_backend.event

import me.longng.finnish_learning_backend.TestcontainersConfiguration
import me.longng.finnish_learning_backend.domain.Role
import me.longng.finnish_learning_backend.persistence.TopicRepository
import me.longng.finnish_learning_backend.persistence.UserRepository
import me.longng.finnish_learning_backend.persistence.UserTopicStatsRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Integration test for the Kafka producer → consumer → DB pipeline.
 *
 * Verifies that a [QuizAnswerEvent] published via [QuizEventProducer]
 * is consumed by [QuizStatsConsumer] and results in an upserted row
 * in the `user_topic_stats` table.
 *
 * Uses Testcontainers for both PostgreSQL and Kafka (via [TestcontainersConfiguration]).
 *
 * NOTE: This test is NOT @Transactional because the Kafka consumer runs in a
 * separate thread. A @Transactional test would make the consumer unable to see
 * the test's DB state (transaction isolation).
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class QuizKafkaIntegrationTest {

    @Autowired
    private lateinit var quizEventProducer: QuizEventProducer

    @Autowired
    private lateinit var userTopicStatsRepository: UserTopicStatsRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var topicRepository: TopicRepository

    @Test
    fun testPublishedEvent_IsConsumedAndCreatesUserTopicStats() {
        val user = userRepository.insert(
            username = "test_user",
            passwordHash = "hashed",
            role = Role.USER,
        )
        val topicId = topicRepository.findAll().first().id

        val event = QuizAnswerEvent(
            userId = user.id,
            cardId = 1, // card ID doesn't matter for stats
            topicId = topicId,
            quality = 4,
            correct = true,
            timestamp = Instant.now(),
        )

        // Act: publish the event
        quizEventProducer.publish(event)

        // Assert: poll until the consumer has processed the event (max 10 seconds)
        val deadline = System.currentTimeMillis() + 10_000
        var stats = userTopicStatsRepository.findByUserAndTopic(user.id, topicId)

        while (stats == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(500)
            stats = userTopicStatsRepository.findByUserAndTopic(user.id, topicId)
        }

        assertNotNull(stats, "Expected user_topic_stats row to be created by Kafka consumer")
        assertEquals(1, stats.totalReviews)
        assertEquals(1, stats.correctReviews)
        assertEquals(1, stats.currentStreak)
        assertEquals(1, stats.bestStreak)
    }

    @Test
    fun testIncorrectAnswerEvent_ResetsStreakAndIncrementsTotalReviews() {
        val user = userRepository.insert(
            username = "streak_user",
            passwordHash = "hashed",
            role = Role.USER,
        )
        val topicId = topicRepository.findAll().first().id

        // First event: correct (sets streak to 1)
        val correctEvent = QuizAnswerEvent(
            userId = user.id,
            cardId = 1,
            topicId = topicId,
            quality = 4,
            correct = true,
            timestamp = Instant.now(),
        )
        quizEventProducer.publish(correctEvent)

        // Wait for first event to be processed
        val firstDeadline = System.currentTimeMillis() + 10_000
        while (
            userTopicStatsRepository.findByUserAndTopic(user.id, topicId) == null &&
            System.currentTimeMillis() < firstDeadline
        ) {
            Thread.sleep(500)
        }

        // Act: publish an incorrect event
        val incorrectEvent = QuizAnswerEvent(
            userId = user.id,
            cardId = 2,
            topicId = topicId,
            quality = 1,
            correct = false,
            timestamp = Instant.now(),
        )
        quizEventProducer.publish(incorrectEvent)

        // Assert: poll until totalReviews reaches 2
        val deadline = System.currentTimeMillis() + 10_000
        var stats = userTopicStatsRepository.findByUserAndTopic(user.id, topicId)

        while ((stats == null || stats.totalReviews < 2) && System.currentTimeMillis() < deadline) {
            Thread.sleep(500)
            stats = userTopicStatsRepository.findByUserAndTopic(user.id, topicId)
        }

        assertNotNull(stats)
        assertEquals(2, stats.totalReviews)
        assertEquals(1, stats.correctReviews)
        assertEquals(0, stats.currentStreak)
        assertEquals(1, stats.bestStreak)
    }
}