package me.longng.finnish_learning_backend.service

import me.longng.finnish_learning_backend.controller.dto.AuthResponse
import me.longng.finnish_learning_backend.domain.Role
import me.longng.finnish_learning_backend.persistence.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Handles user registration and login.
 * Registration creates a new user with the USER role and immediately returns a JWT token
 * (so the user is automatically logged in after registering).
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder,
) {
    private val logger = LoggerFactory.getLogger(AuthService::class.java)

    /**
     * Registers a new user with the USER role.
     */
    @Transactional
    fun register(username: String, password: String): AuthResponse {
        require(username.isNotBlank()) { "Username must not be blank" }
        require(password.length >= 6) { "Password must be at least 6 characters" }

        if (userRepository.findByUsername(username) != null) {
            throw UsernameAlreadyExistsException(username)
        }

        val hashedPassword = requireNotNull(passwordEncoder.encode(password)) { "Password encoding failed" }
        val user = userRepository.insert(username, hashedPassword, Role.USER)
        val token = jwtService.generateToken(user.id, user.username, user.role)

        logger.info("Registered new user: username={}, id={}", user.username, user.id)

        return AuthResponse(
            token = token,
            userId = user.id,
            username = user.username,
            role = user.role.name,
        )
    }

    /**
     * Authenticates a user and returns a JWT token.
     */
    @Transactional(readOnly = true)
    fun login(username: String, password: String): AuthResponse {
        val user = userRepository.findByUsername(username)
            ?: throw InvalidCredentialsException()

        if (!passwordEncoder.matches(password, user.passwordHash)) {
            throw InvalidCredentialsException()
        }

        val token = jwtService.generateToken(user.id, user.username, user.role)

        logger.info("User logged in: username={}, id={}", user.username, user.id)

        return AuthResponse(
            token = token,
            userId = user.id,
            username = user.username,
            role = user.role.name,
        )
    }
}

/**
 * Mapped to HTTP 409 (Conflict) by [GlobalExceptionHandler].
 */
class UsernameAlreadyExistsException(username: String) :
    RuntimeException("Username '$username' is already taken")

/**
 * Mapped to HTTP 401 (Unauthorized) by [GlobalExceptionHandler].
 */
class InvalidCredentialsException :
    RuntimeException("Invalid username or password")