package me.longng.finnish_learning_backend.service.ask.mcp

import me.longng.finnish_learning_backend.config.AskProperties
import me.longng.finnish_learning_backend.config.McpClientConfig
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Talks to the real profile MCP server of `npm run dev` in `mcp-server/` (the example profile, no
 * auth). Not part of `./gradlew test`: start the server, then run `./gradlew manualTest`.
 */
@Tag("manual")
class McpProfileClientManualTest {

    private val settings = AskProperties.Mcp(
        url = "http://localhost:3001/mcp",
        region = "eu-north-1",
        signRequests = false,
        connectTimeoutMs = 3000,
        requestTimeoutMs = 10000,
    )

    @Test
    fun testLocalServer_ListsToolsAndReadsIndex() {
        McpClientConfig.createMcpClient(settings, signer = null).use { mcpClient ->
            val client = SigV4McpProfileClient(mcpClient)

            assertEquals(setOf("list_entries", "get_entry"), client.listTools().map { it.name }.toSet())
            assertTrue(client.readIndex().contains("experience/acme-backend"))
            val entry = client.callTool("get_entry", mapOf("id" to "experience/acme-backend"))
            assertTrue(!entry.isError && entry.text.contains("Kafka"), entry.text)
            assertTrue(client.callTool("get_entry", mapOf("id" to "experience/nope")).isError)
        }
    }
}