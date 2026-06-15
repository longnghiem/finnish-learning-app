package me.longng.finnish_learning_mobile.data.repositories

import me.longng.finnish_learning_mobile.data.api.dto.toDomain
import me.longng.finnish_learning_mobile.data.api.service.TopicApi
import me.longng.finnish_learning_mobile.data.model.Topic
import me.longng.finnish_learning_mobile.util.AppResult
import me.longng.finnish_learning_mobile.util.runCatchingApp

class TopicRepository(private val api: TopicApi) {
    suspend fun queryTopics(): AppResult<List<Topic>> = runCatchingApp {
        api.fetchTopics().map { it.toDomain() }
    }
}