package me.longng.finnish_learning_backend.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

/**
 * Configuration for the Groq HTTP client used by the sentence-evaluation feature.
 *
 * The [groqRestClient] bean is pre-bound to the configured base URL. The API key is
 * intentionally *not* bound here — it is injected directly into `GroqClient` and
 * attached per-request as an `Authorization: Bearer …` header. This separation keeps
 * the bean construction safe even when the key is blank at startup, and isolates the
 * key to the single component that needs it.
 */
@Configuration
class GroqConfig(
    @Value($$"${app.groq.base-url}") private val baseUrl: String,
) {
    @Bean
    fun groqRestClient(): RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .build()
}