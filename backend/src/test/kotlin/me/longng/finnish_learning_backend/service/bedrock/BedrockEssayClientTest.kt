package me.longng.finnish_learning_backend.service.bedrock

import me.longng.finnish_learning_backend.controller.dto.EssayCefrLevel
import me.longng.finnish_learning_backend.controller.dto.EssayIssueKind
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.core.io.ByteArrayResource
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse
import software.amazon.awssdk.services.bedrockruntime.model.Message
import software.amazon.awssdk.services.bedrockruntime.model.OutputFormatType
import software.amazon.awssdk.services.bedrockruntime.model.StopReason
import software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BedrockEssayClientTest {

    private val bedrockRuntimeClient: BedrockRuntimeClient = mock()

    private val client = BedrockEssayClient(
        bedrockRuntimeClient = bedrockRuntimeClient,
        objectMapper = jacksonObjectMapper(),
        modelId = MODEL_ID,
        maxTokens = 2000,
        systemPromptResource = ByteArrayResource("You are a Finnish teacher.".toByteArray()),
    )

    @Test
    fun testEvaluate_ValidResponse() {
        stubConverseWith(
            """
            {
              "cefrLevel": "B1.1",
              "onTopic": true,
              "issues": [
                { "kind": "GRAMMAR", "original": "minä menen kauppaan eilen", "suggestion": "minä menin kauppaan eilen" },
                { "kind": "TYPO", "original": "kirjastoo", "suggestion": "kirjastoon" }
              ],
              "feedback": "Good structure. Watch your past tense."
            }
            """,
        )

        val result = client.evaluate(PROMPT_TITLE, ESSAY)

        assertEquals(EssayCefrLevel.B1_1, result.cefrLevel)
        assertTrue(result.onTopic)
        assertEquals(2, result.issues.size)
        assertEquals(EssayIssueKind.GRAMMAR, result.issues[0].kind)
        assertEquals("minä menen kauppaan eilen", result.issues[0].original)
        assertEquals("minä menin kauppaan eilen", result.issues[0].suggestion)
        assertEquals(EssayIssueKind.TYPO, result.issues[1].kind)
        assertEquals("Good structure. Watch your past tense.", result.feedback)
    }

    @Test
    fun testEvaluate_SendsSchemaConstrainedRequest() {
        stubConverseWith(MINIMAL_BODY)

        client.evaluate(PROMPT_TITLE, ESSAY)

        val captor = argumentCaptor<ConverseRequest>()
        verify(bedrockRuntimeClient).converse(captor.capture())
        val request = captor.firstValue

        assertEquals(MODEL_ID, request.modelId())
        assertEquals(
            OutputFormatType.JSON_SCHEMA,
            request.outputConfig().textFormat().type(),
            "the response must be schema-constrained, not prompt-coaxed",
        )
        assertEquals(
            BedrockEssayClient.ESSAY_RESPONSE_SCHEMA,
            request.outputConfig().textFormat().structure().jsonSchema().schema(),
        )
        assertTrue(
            request.messages().single().content().single().text().contains(ESSAY),
            "the essay must reach the model",
        )
    }

    @Test
    fun testEvaluate_EmptyIssues() {
        stubConverseWith(
            """{ "cefrLevel": "A2.1", "onTopic": true, "issues": [], "feedback": "Flawless." }""",
        )

        val result = client.evaluate(PROMPT_TITLE, ESSAY)

        assertTrue(result.issues.isEmpty(), "an empty issues array is a valid result, not an error")
        assertEquals(EssayCefrLevel.A2_1, result.cefrLevel)
    }

    @Test
    fun testEvaluate_MissingOptionalFeedback() {
        stubConverseWith("""{ "cefrLevel": "A1.2", "onTopic": false, "issues": [] }""")

        val result = client.evaluate(PROMPT_TITLE, ESSAY)

        assertNull(result.feedback, "feedback is optional in the schema and nullable in the DTO")
    }

    @Test
    fun testEvaluate_UnparseableBody() {
        stubConverseWith("I'm sorry, I cannot grade this essay.")

        assertThrows<EssayEvaluationUpstreamException> { client.evaluate(PROMPT_TITLE, ESSAY) }
    }

    @Test
    fun testEvaluate_UnknownCefrLevel() {
        // Well-formed JSON carrying a level outside EssayCefrLevel. Jackson raises
        // InvalidFormatException; it must surface as a 502, never as an unhandled 500.
        stubConverseWith("""{ "cefrLevel": "C1", "onTopic": true, "issues": [] }""")

        assertThrows<EssayEvaluationUpstreamException> { client.evaluate(PROMPT_TITLE, ESSAY) }
    }

    @Test
    fun testEvaluate_EmptyContent() {
        whenever(bedrockRuntimeClient.converse(any<ConverseRequest>())).thenReturn(
            ConverseResponse.builder()
                .output(ConverseOutput.fromMessage(Message.builder().role(ConversationRole.ASSISTANT).build()))
                .stopReason(StopReason.MAX_TOKENS)
                .build(),
        )

        assertThrows<EssayEvaluationUpstreamException> { client.evaluate(PROMPT_TITLE, ESSAY) }
    }

    @Test
    fun testEvaluate_SdkFailure() {
        whenever(bedrockRuntimeClient.converse(any<ConverseRequest>()))
            .thenThrow(SdkClientException.create("Unable to load credentials"))

        assertThrows<EssayEvaluationUpstreamException> { client.evaluate(PROMPT_TITLE, ESSAY) }
    }

    @Test
    fun testEvaluate_BlankEssay() {
        assertThrows<IllegalArgumentException> { client.evaluate(PROMPT_TITLE, "   ") }
    }

    @Test
    fun testEvaluate_AccessDenied() {
        whenever(bedrockRuntimeClient.converse(any<ConverseRequest>()))
            .thenThrow(AccessDeniedException.builder().message("You don't have access to the model").build())

        val ex = assertThrows<EssayEvaluationMisconfiguredException> {
            client.evaluate(PROMPT_TITLE, ESSAY)
        }
        assertTrue(
            ex.message!!.contains("Model access") || ex.message!!.contains("model access"),
            "the message must name model access as a likely cause",
        )
        assertTrue(ex.message!!.contains("bedrock:InvokeModel"), "and the IAM permission as the other")
    }

    /** Wraps [body] in the minimal Converse response shape the client reads. */
    private fun stubConverseWith(body: String) {
        whenever(bedrockRuntimeClient.converse(any<ConverseRequest>())).thenReturn(
            ConverseResponse.builder()
                .output(
                    ConverseOutput.fromMessage(
                        Message.builder()
                            .role(ConversationRole.ASSISTANT)
                            .content(ContentBlock.fromText(body.trimIndent()))
                            .build(),
                    ),
                )
                .stopReason(StopReason.END_TURN)
                .build(),
        )
    }

    companion object {
        private const val MODEL_ID = "eu.anthropic.claude-haiku-4-5-20251001-v1:0"
        private const val PROMPT_TITLE = "Menet kahvilaan, mutta kaikki pöydät ovat likaisia."
        private const val ESSAY = "Minä menen kahvilaan ja pöydät ovat likaisia. Sanon tarjoilijalle."
        private const val MINIMAL_BODY = """{ "cefrLevel": "A2.2", "onTopic": true, "issues": [] }"""
    }
}