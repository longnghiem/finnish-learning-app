package me.longng.finnish_learning_mobile.data.repositories

import me.longng.finnish_learning_mobile.data.api.dto.toDomain
import me.longng.finnish_learning_mobile.data.api.service.ProgressApi
import me.longng.finnish_learning_mobile.data.model.Dashboard
import me.longng.finnish_learning_mobile.data.model.TopicProgress
import me.longng.finnish_learning_mobile.util.AppResult
import me.longng.finnish_learning_mobile.util.runCatchingApp

class ProgressRepository(private val api: ProgressApi) {
    suspend fun fetchTopicProgress(): AppResult<List<TopicProgress>> = runCatchingApp {
        api.fetchTopicProgress().map { it.toDomain() }
    }

    suspend fun fetchDashboard(): AppResult<Dashboard> = runCatchingApp {
        api.fetchDashboard().toDomain()
    }
}