package me.longng.finnish_learning_backend.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * CORS configuration for the Finnish Learning App.
 *
 * Exposes a [CorsConfigurationSource] bean which Spring Security picks up
 * via `http.cors { }` in [WebSecurityConfig]. Registering CORS at the
 * security layer (rather than as a [WebMvcConfigurer]) is required so that
 * preflight `OPTIONS` requests receive the proper `Access-Control-Allow-*`
 * headers before the JWT authentication filter rejects them as unauthenticated.
 *
 * The allow-list is read from the `app.cors.allowed-origins` property
 * (overridable via the `APP_CORS_ALLOWED_ORIGINS` environment variable) as a
 * comma-separated list. An empty value means no CORS configuration is
 * registered — relevant when the app runs behind a reverse proxy that serves
 * the SPA and APIs from the same origin (e.g. the production demo on EC2).
 *
 * Exposes the `Authorization` header and enables credentials so JWT tokens
 * can be sent from the frontend when cross-origin access is enabled.
 */
@Configuration
class CorsConfig(
    @Value("\${app.cors.allowed-origins}") private val allowedOriginsRaw: String,
) {
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val origins = allowedOriginsRaw
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val source = UrlBasedCorsConfigurationSource()
        if (origins.isNotEmpty()) {
            val cfg = CorsConfiguration().apply {
                allowedOrigins = origins
                allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
                allowedHeaders = listOf("*")
                exposedHeaders = listOf("Authorization")
                allowCredentials = true
            }
            source.registerCorsConfiguration("/api/**", cfg)
        }
        return source
    }
}