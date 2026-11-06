package me.longng.finnish_learning_backend.config

import io.modelcontextprotocol.client.McpClient
import io.modelcontextprotocol.client.McpSyncClient
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer
import io.modelcontextprotocol.spec.McpSchema
import me.longng.finnish_learning_backend.service.ask.mcp.SigV4McpProfileClient
import me.longng.finnish_learning_backend.service.ask.mcp.SigV4RequestSigner
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import java.net.URI
import java.time.Duration

/**
 * The MCP client that [SigV4McpProfileClient] uses to reach the profile server.
 *
 * There is **one** [McpSyncClient] for the whole backend. Its first use costs 3 requests (the MCP
 * handshake, see [createMcpClient]); after that every tool call or index read is 1 request, so
 * every recruiter question doesn't pay for a new handshake. Creating the bean doesn't connect: the
 * app starts even when the server is down.
 */
@Configuration
class McpClientConfig(
    private val properties: AskProperties,
) {
    private val logger = LoggerFactory.getLogger(McpClientConfig::class.java)

    /**
     * The AWS credentials for signing: the EC2 instance profile in production, the local AWS
     * profile (`~/.aws`) in development. Looked up on first use, not at startup.
     */
    @Bean
    fun mcpCredentialsProvider(): AwsCredentialsProvider = DefaultCredentialsProvider.builder().build()

    @Bean(destroyMethod = "closeGracefully")
    fun mcpSyncClient(mcpCredentialsProvider: AwsCredentialsProvider): McpSyncClient {
        val settings = properties.mcp
        val signer = if (settings.signRequests) {
            SigV4RequestSigner(mcpCredentialsProvider, settings.region)
        } else {
            logger.warn("app.ask.mcp.sign-requests is false: MCP requests go out unsigned. Use this only with the local dev server.")
            null
        }
        return createMcpClient(settings, signer)
    }

    companion object {
        /** Name and version the client reports to the server in the handshake. */
        private val CLIENT_INFO = McpSchema.Implementation.builder("opisuomea-backend", "0.1.0").build()

        /**
         * Builds an MCP client for [settings]; the tests use it too, so they test the same setup.
         *
         * The SDK connects on first use with a handshake of 3 requests: `initialize`, one GET (the
         * SDK always opens an optional stream for server messages; the profile server answers 405,
         * which the SDK accepts), and the `notifications/initialized` message. If the handshake
         * fails, the SDK tries it again on the next call. There are no other retries: a failed
         * call already took up to [AskProperties.Mcp.requestTimeoutMs].
         *
         * @param signer signs every request, or null to send them unsigned (local dev server only)
         */
        fun createMcpClient(settings: AskProperties.Mcp, signer: McpSyncHttpClientRequestCustomizer?): McpSyncClient {
            val url = URI.create(settings.url)
            val transport = HttpClientStreamableHttpTransport.builder("${url.scheme}://${url.rawAuthority}")
                .endpoint(url.rawPath.ifEmpty { "/" })
                .connectTimeout(Duration.ofMillis(settings.connectTimeoutMs))
                .apply { if (signer != null) httpRequestCustomizer(signer) }
                .build()
            val timeout = Duration.ofMillis(settings.requestTimeoutMs)
            return McpClient.sync(transport)
                .requestTimeout(timeout)
                .initializationTimeout(timeout)
                .clientInfo(CLIENT_INFO)
                .build()
        }
    }
}