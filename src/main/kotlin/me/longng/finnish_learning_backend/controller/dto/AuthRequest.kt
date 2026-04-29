package me.longng.finnish_learning_backend.controller.dto

/**
 * Request body for both registration and login.
 */
data class AuthRequest(
    val username: String,
    val password: String,
)