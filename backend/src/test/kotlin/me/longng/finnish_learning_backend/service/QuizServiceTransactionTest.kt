package me.longng.finnish_learning_backend.service

import me.longng.finnish_learning_backend.TestcontainersConfiguration
import me.longng.finnish_learning_backend.domain.Role
import me.longng.finnish_learning_backend.event.QuizEventProducer
import me.longng.finnish_learning_backend.persistence.CardRepository
import me.longng.finnish_learning_backend.persistence.TopicRepository
import me.longng.finnish_learning_backend.persistence.UserRepository
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.Test

/**
 * Verifies the post-commit publishing contract of [QuizService.submitAnswer]:
 *
 * - A rolled-back transaction must publish NOTHING to Kafka.
 * - A committed transaction must publish exactly one event.
 *
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class QuizServiceTransactionTest {
    @Autowired
    private lateinit var quizService: QuizService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var topicRepository: TopicRepository

    @Autowired
    private lateinit var cardRepository: CardRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @MockitoBean
    private lateinit var quizEventProducer: QuizEventProducer

    @Test
    fun testSubmitAnswer_RollbackDoesNotPublish() {
        val transactionTemplate = TransactionTemplate(transactionManager)

        transactionTemplate.executeWithoutResult { status ->
            val user = userRepository.insert(
                username = "rollback_user",
                passwordHash = "hashed",
                role = Role.USER,
            )
            val topicId = topicRepository.findAll().first().id
            val card = cardRepository.findNewCardsForUser(user.id, topicId, 1).first()
            quizService.submitAnswer(userId = user.id, cardId = card.id, quality = 4)
            status.setRollbackOnly() // force rollback → AFTER_COMMIT listener must NOT fire
        }

        verify(quizEventProducer, never()).publish(any())
    }


    @Test
    fun testSubmitAnswer_CommitPublishesEvent() {
        val transactionTemplate = TransactionTemplate(transactionManager)

        transactionTemplate.executeWithoutResult {
            val user = userRepository.insert(
                username = "commit_user",
                passwordHash = "hashed",
                role = Role.USER,
            )
            val topicId = topicRepository.findAll().first().id
            val card = cardRepository.findNewCardsForUser(user.id, topicId, 1).first()
            quizService.submitAnswer(userId = user.id, cardId = card.id, quality = 4)
        }

        verify(quizEventProducer, times(1)).publish(any())
    }
}