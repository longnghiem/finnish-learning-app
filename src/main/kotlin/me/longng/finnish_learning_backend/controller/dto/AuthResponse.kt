package me.longng.finnish_learning_backend.controller.dto

/**
 * Response returned by the register and login endpoints.
 */
data class AuthResponse(
    val token: String,
    val userId: Int,
    val username: String,
    val role: String,
)