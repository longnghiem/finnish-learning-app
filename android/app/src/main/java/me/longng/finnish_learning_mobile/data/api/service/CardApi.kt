package me.longng.finnish_learning_mobile.data.api.service

import me.longng.finnish_learning_mobile.data.api.dto.CardResponse
import me.longng.finnish_learning_mobile.data.api.dto.SearchType
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CardApi {

    @GET("api/cards")
    suspend fun fetchCards(
        @Query("topicId") topicId: Int? = null,
        @Query("searchType") searchType: SearchType? = null,
        @Query("searchTerm") searchTerm: String? = null,
    ): List<CardResponse>

    @GET("api/cards/{id}")
    suspend fun fetchCardById(@Path("id") id: Int): CardResponse
}