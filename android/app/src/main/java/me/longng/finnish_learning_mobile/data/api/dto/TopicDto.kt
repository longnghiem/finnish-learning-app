package me.longng.finnish_learning_mobile.data.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TopicResponse(
    val id: Int,
    val name: String,
    val totalCards: Int,
)