package me.longng.finnish_learning_mobile.data.repositories

import me.longng.finnish_learning_mobile.data.api.dto.SearchType
import me.longng.finnish_learning_mobile.data.api.dto.toDomain
import me.longng.finnish_learning_mobile.data.api.service.CardApi
import me.longng.finnish_learning_mobile.data.model.Card
import me.longng.finnish_learning_mobile.util.AppResult
import me.longng.finnish_learning_mobile.util.runCatchingApp

class CardRepository(private val api: CardApi) {
    suspend fun search(
        topicId: Int,
        searchType: SearchType,
        searchTerm: String
    ): AppResult<List<Card>> {
        return runCatchingApp {
            api.fetchCards(
                topicId = topicId,
                searchType = SearchType.valueOf(searchType.name),
                searchTerm = searchTerm,
            ).map { it.toDomain() }
        }
    }
}