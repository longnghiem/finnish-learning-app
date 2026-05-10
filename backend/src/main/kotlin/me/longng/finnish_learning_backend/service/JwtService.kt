package me.longng.finnish_learning_backend.service

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import me.longng.finnish_learning_backend.domain.Role
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${app.jwt.secret}") private val secret: String,
    @Value("\${app.jwt.expiration-ms}") private val expirationMs: Long,
) {
    private val signingKey: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())

    /**
     * Generates a JWT for the given user.
     * The token is signed with HMAC-SHA256 and expires after [expirationMs] milliseconds.
     */
    fun generateToken(userId: Int, username: String, role: Role): String {
        val now = Date()
        val expiration = Date(now.time + expirationMs)

        return Jwts.builder()
            .subject(userId.toString())
            .claim("username", username)
            .claim("role", role.name)
            .issuedAt(now)
            .expiration(expiration)
            .signWith(signingKey)
            .compact()
    }

    /**
     * Validates the given JWT and extracts the user ID from the `sub` claim.
     * @return the user ID if the token is valid and not expired, or `null` otherwise.
     */
    fun validateTokenAndGetUserId(token: String): Int? =
        try {
            val claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .payload

            claims.subject?.toIntOrNull()
        } catch (_: JwtException) {
            null
        }

    /**
     * Extracts the [Role] from a valid JWT's `role` claim.
     * @return the role if the token is valid and contains a recognized role, or `null` otherwise.
     */
    fun extractRole(token: String): Role? =
        try {
            val claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .payload

            val roleName = claims["role", String::class.java]
            roleName?.let { Role.valueOf(it) }
        } catch (_: JwtException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

}