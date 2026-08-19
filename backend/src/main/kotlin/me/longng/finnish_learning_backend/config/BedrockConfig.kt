package me.longng.finnish_learning_backend.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import java.time.Duration

/**
 * Configuration for the Amazon Bedrock client used by the essay-evaluation feature.
 *
 * **Why the read timeout defaults to 120 s.** Bedrock compiles a new JSON Schema into a
 * decoding grammar the first time it sees it, which can take a few minutes; the compiled
 * grammar is then cached for 24 h per account. A conventional 30 s read timeout would turn
 * the first call after every schema change into a hard failure. Later we add a post-deploy
 * warm-up call so no learner ever absorbs that latency, but the timeout has to tolerate it
 * regardless.
 */
@Configuration
class BedrockConfig (
    @Value($$"${app.bedrock.region}") private val region: String,
    @Value($$"${app.bedrock.connect-timeout-ms}") private val connectTimeoutMs: Long,
    @Value($$"${app.bedrock.read-timeout-ms}") private val readTimeoutMs: Long,
) {
    @Bean
    fun bedrockRuntimeClient(): BedrockRuntimeClient =
        BedrockRuntimeClient.builder()
            .region(Region.of(region))
            .httpClient(
                UrlConnectionHttpClient.builder()
                    .connectionTimeout(Duration.ofMillis(connectTimeoutMs))
                    .socketTimeout(Duration.ofMillis(readTimeoutMs))
                    .build(),
            )
            .build()
}