package me.longng.finnish_learning_mobile.data.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AuthRequest(
    val username: String,
    val password: String,
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    val token: String,
    val userId: Int,
    val username: String,
    val role: String,
)