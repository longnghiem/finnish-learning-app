package me.longng.finnish_learning_backend.config

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
 * Allows the Vite dev server (localhost:5173) to make cross-origin requests.
 * Exposes the `Authorization` header and enables credentials so that JWT
 * tokens can be sent from the frontend.
 */
@Configuration
class CorsConfig {
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val cfg = CorsConfiguration().apply {
            allowedOrigins = listOf("http://localhost:5173")
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            exposedHeaders = listOf("Authorization")
            allowCredentials = true
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/api/**", cfg)
        }
    }
}