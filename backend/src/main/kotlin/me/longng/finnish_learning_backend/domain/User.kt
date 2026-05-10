package me.longng.finnish_learning_backend.domain

import java.time.Instant

data class User(
    val id: Int,
    val username: String,
    val passwordHash: String,
    val role: Role,
    val createdAt: Instant,
)

enum class Role {
    USER,
    ADMIN,
}