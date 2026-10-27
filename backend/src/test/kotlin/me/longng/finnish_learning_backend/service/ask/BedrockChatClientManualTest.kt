package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.config.testAskProperties
import me.longng.finnish_learning_backend.service.ask.BedrockChatClientTest.Companion.GET_ENTRY_TOOL
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertTrue

/**
 * One tool round trip against the real Bedrock (eu-north-1, Haiku 4.5), with the local AWS
 * credentials. It checks what the unit tests can't: that Bedrock accepts the requests exactly as
 * [BedrockChatClient] builds them, in particular a cache point right after tool results, and
 * tool schemas without their `$schema` line. Costs about $0.005.
 *
 * Not part of `./gradlew test`. Run it with:
 * `./gradlew manualTest --tests '*BedrockChatClientManualTest*'`
 */
@Tag("manual")
class BedrockChatClientManualTest {

    @Test
    fun testConverse_RealToolRoundTrip() {
        BedrockRuntimeClient.builder().region(Region.EU_NORTH_1).build().use { bedrock ->
            val client = BedrockChatClient(bedrock, JsonMapper.builder().build(), testAskProperties())
            val system = "You answer questions about a developer's profile. Always read an entry with get_entry before answering."
            val question = BedrockChatClient.userText("What does the profile entry with the id 'about' say? Read it first.")

            val first = client.converse(system, listOf(question), listOf(GET_ENTRY_TOOL), maxTokens = 200)
            assertTrue(first.wantsTools, "The model should have asked for get_entry, got: ${first.stopReason}")

            val results = first.toolRequests.map { request ->
                ToolOutcome(request.toolUseId, """{"id":"about","body":"A fictional backend developer who likes Kotlin."}""", isError = false)
            }
            val messages = listOf(
                question,
                first.message,
                BedrockChatClient.toolResults(results, note = ChatService.TOOL_LIMIT_NOTE),
            )
            val second = client.converse(system, messages, listOf(GET_ENTRY_TOOL), maxTokens = 200)

            assertTrue(second.text.isNotBlank(), "The final call should answer with text, got: ${second.stopReason}")
            println("Answer: ${second.text}\nUsage: ${first.usage} + ${second.usage}")
        }
    }
}