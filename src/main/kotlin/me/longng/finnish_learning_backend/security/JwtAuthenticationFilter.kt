package me.longng.finnish_learning_backend.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.longng.finnish_learning_backend.service.JwtService
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * JWT authentication filter that runs once per request.
 *
 * Extracts the Bearer token from the `Authorization` header, validates it via [JwtService],
 * and populates the [SecurityContextHolder] with the authenticated user's identity and role.
 *
 * If the token is missing or invalid, the filter does nothing — Spring Security's route-level
 * authorization rules will then decide whether to allow or reject the request.
 */
@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authHeader = request.getHeader("Authorization")

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val token = authHeader.substring(7) // Remove "Bearer " prefix

        val userId = jwtService.validateTokenAndGetUserId(token)
        if (userId == null) {
            filterChain.doFilter(request, response)
            return
        }

        val role = jwtService.extractRole(token)
        // Spring Security expects the ROLE_ prefix for hasRole() checks.
        // e.g., hasRole("ADMIN") checks for authority "ROLE_ADMIN".
        val authorities = role?.let {
            listOf(SimpleGrantedAuthority("ROLE_${it.name}"))
        } ?: emptyList()

        val authToken = UsernamePasswordAuthenticationToken(
            userId,    // principal — the userId (Int)
            null,      // credentials — not needed, the JWT itself is the credential
            authorities,
        )

        SecurityContextHolder.getContext().authentication = authToken
        filterChain.doFilter(request, response)
    }
}