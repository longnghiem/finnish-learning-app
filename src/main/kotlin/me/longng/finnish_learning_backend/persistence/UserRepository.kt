package me.longng.finnish_learning_backend.persistence

import me.longng.finnish_learning_backend.domain.Role
import me.longng.finnish_learning_backend.domain.User

interface UserRepository {
    fun insert(
        username: String,
        passwordHash: String,
        role: Role
    ): User
    fun findByUsername(username: String): User?
    fun findById(id: Int): User?
}