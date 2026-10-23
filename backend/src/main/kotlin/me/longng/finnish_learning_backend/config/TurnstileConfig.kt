package me.longng.finnish_learning_backend.config

import me.longng.finnish_learning_backend.service.ask.TurnstileClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

/**
 * The HTTP client [TurnstileClient] uses to call Cloudflare.
 *
 * Both timeouts are explicit and short. Without them a slow Cloudflare would hold a request
 * thread for as long as the operating system allows, and a visitor would wait with no answer.
 */
@Configuration
class TurnstileConfig(
    private val properties: AskProperties,
) {
    @Bean
    fun turnstileRestClient(): RestClient {
        val settings = properties.turnstile
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(settings.connectTimeoutMs))
            .build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(Duration.ofMillis(settings.readTimeoutMs))
        }
        return RestClient.builder()
            .requestFactory(requestFactory)
            .build()
    }
}