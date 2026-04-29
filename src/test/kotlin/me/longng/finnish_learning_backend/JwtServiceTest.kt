package me.longng.finnish_learning_backend

import me.longng.finnish_learning_backend.domain.Role
import me.longng.finnish_learning_backend.service.JwtService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JwtServiceTest {

    // A secret that is at least 256 bits (32 bytes) for HMAC-SHA256
    private val testSecret = "test-secret-key-that-is-at-least-256-bits-long!!"
    private val oneHourMs = 3_600_000L

    private fun createService(
        secret: String = testSecret,
        expirationMs: Long = oneHourMs,
    ) = JwtService(secret = secret, expirationMs = expirationMs)

    @Test
    fun testGenerateToken_NonEmptyToken() {
        val service = createService()
        val token = service.generateToken(userId = 1, username = "testuser", role = Role.USER)
        assertNotNull(token)
        assert(token.isNotBlank())
    }

    @Test
    fun testValidateTokenAndGetUserId_ValidToken() {
        val service = createService()
        val token = service.generateToken(userId = 42, username = "testuser", role = Role.USER)

        val userId = service.validateTokenAndGetUserId(token)

        assertEquals(42, userId)
    }

    @Test
    fun testValidateTokenAndGetUserId_InvalidToken() {
        val service = createService()

        val userId = service.validateTokenAndGetUserId("invalid.token.string")

        assertNull(userId)
    }

    @Test
    fun testValidateTokenAndGetUserId_ExpiredToken() {
        // Create a service with 0ms expiration (token is immediately expired)
        val service = createService(expirationMs = 0)
        val token = service.generateToken(userId = 1, username = "testuser", role = Role.USER)

        val userId = service.validateTokenAndGetUserId(token)

        assertNull(userId)
    }

    @Test
    fun testValidateTokenAndGetUserId_DifferentSecret() {
        val service1 = createService(secret = "first-secret-key-that-is-at-least-256-bits-long!!")
        val service2 = createService(secret = "other-secret-key-that-is-at-least-256-bits-long!!")

        val token = service1.generateToken(userId = 1, username = "testuser", role = Role.USER)
        val userId = service2.validateTokenAndGetUserId(token)

        assertNull(userId)
    }

    @Test
    fun testExtractRole_CorrectRole() {
        val service = createService()

        val userToken = service.generateToken(userId = 1, username = "user", role = Role.USER)
        val adminToken = service.generateToken(userId = 2, username = "admin", role = Role.ADMIN)

        assertEquals(Role.USER, service.extractRole(userToken))
        assertEquals(Role.ADMIN, service.extractRole(adminToken))
    }

    @Test
    fun testExtractRole_InvalidToken() {
        val service = createService()

        assertNull(service.extractRole("invalid.token.string"))
    }
}