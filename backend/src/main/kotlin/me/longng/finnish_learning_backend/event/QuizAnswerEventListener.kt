package me.longng.finnish_learning_backend.event

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Forwards a [QuizAnswerEvent] to Kafka, but ONLY after the surrounding database
 * transaction has committed.
 */
@Component
class QuizAnswerEventListener(
    private val quizEventProducer: QuizEventProducer
) {
    private val logger = LoggerFactory.getLogger(QuizAnswerEventListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onQuizAnswerEvent(event: QuizAnswerEvent) {
        logger.debug("Transaction committed — forwarding QuizAnswerEvent to Kafka: {}", event)
        quizEventProducer.publish(event)
    }

}