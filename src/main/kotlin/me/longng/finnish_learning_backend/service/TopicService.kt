package me.longng.finnish_learning_backend.service

import me.longng.finnish_learning_backend.domain.Topic
import me.longng.finnish_learning_backend.persistence.TopicRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TopicService(
    private val topicRepository: TopicRepository,
) {
    fun getAllTopics(): List<Topic> = topicRepository.findAll()

    fun getTopicById(id: Int): Topic =
        topicRepository.findById(id) ?: throw TopicNotFoundException(id)
}