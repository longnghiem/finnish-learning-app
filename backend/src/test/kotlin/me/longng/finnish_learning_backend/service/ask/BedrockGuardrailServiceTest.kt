package me.longng.finnish_learning_backend.service.ask

import com.sun.net.httpserver.HttpServer
import me.longng.finnish_learning_backend.config.testAskProperties
import me.longng.finnish_learning_backend.domain.ask.AskMode
import me.longng.finnish_learning_backend.domain.ask.GuardrailOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailChecksContentFilterResult
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailChecksContentFilterResultEntry
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailChecksContentFilterUsage
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailChecksPromptAttackResult
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailChecksPromptAttackResultEntry
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailChecksPromptAttackUsage
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailChecksResults
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailChecksRole
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailChecksUsageResults
import software.amazon.awssdk.services.bedrockruntime.model.InvokeGuardrailChecksRequest
import software.amazon.awssdk.services.bedrockruntime.model.InvokeGuardrailChecksResponse
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BedrockGuardrailServiceTest {

    companion object {
        private const val MESSAGE = "Has Long worked with Kafka?"

        private val CONTENT_CATEGORIES = listOf("VIOLENCE", "HATE", "SEXUAL", "MISCONDUCT", "INSULTS")
        private val PROMPT_ATTACK_CATEGORIES = listOf("JAILBREAK", "PROMPT_INJECTION", "PROMPT_LEAKAGE")

        /**
         * A real answer from Bedrock (eu-north-1, 2026-10-17) to a "DAN" jailbreak: misconduct
         * and jailbreak at 1.0, everything else 0.0. Note the order: not the order requested.
         */
        private val REAL_JAILBREAK_ANSWER = """
            {
              "results": {
                "contentFilter": {"results": [
                  {"category": "VIOLENCE", "severityScore": 0.0},
                  {"category": "MISCONDUCT", "severityScore": 1.0},
                  {"category": "HATE", "severityScore": 0.0},
                  {"category": "SEXUAL", "severityScore": 0.0},
                  {"category": "INSULTS", "severityScore": 0.0}
                ]},
                "promptAttack": {"results": [
                  {"category": "JAILBREAK", "severityScore": 1.0},
                  {"category": "PROMPT_INJECTION", "severityScore": 0.0},
                  {"category": "PROMPT_LEAKAGE", "severityScore": 0.0}
                ]}
              },
              "usage": {"contentFilter": {"textUnits": 1}, "promptAttack": {"textUnits": 1}}
            }
        """.trimIndent()
    }

    private val bedrockRuntimeClient: BedrockRuntimeClient = mock()

    private val service = BedrockGuardrailService(bedrockRuntimeClient, testAskProperties())

    /**
     * An answer with every category at 0.0 except [scores], and [units] text units per check.
     * Pass `null` for [units] to leave out the usage block.
     */
    private fun answer(scores: Map<String, Double> = emptyMap(), units: Int? = 1): InvokeGuardrailChecksResponse =
        InvokeGuardrailChecksResponse.builder()
            .results(
                GuardrailChecksResults.builder()
                    .contentFilter(
                        GuardrailChecksContentFilterResult.builder()
                            .results(
                                CONTENT_CATEGORIES.map { category ->
                                    GuardrailChecksContentFilterResultEntry.builder()
                                        .category(category)
                                        .severityScore(scores[category] ?: 0.0)
                                        .build()
                                },
                            )
                            .build(),
                    )
                    .promptAttack(
                        GuardrailChecksPromptAttackResult.builder()
                            .results(
                                PROMPT_ATTACK_CATEGORIES.map { category ->
                                    GuardrailChecksPromptAttackResultEntry.builder()
                                        .category(category)
                                        .severityScore(scores[category] ?: 0.0)
                                        .build()
                                },
                            )
                            .build(),
                    )
                    .build(),
            )
            .apply {
                if (units != null) {
                    usage(
                        GuardrailChecksUsageResults.builder()
                            .contentFilter(GuardrailChecksContentFilterUsage.builder().textUnits(units).build())
                            .promptAttack(GuardrailChecksPromptAttackUsage.builder().textUnits(units).build())
                            .build(),
                    )
                }
            }
            .build()

    /** A complete [answer] whose prompt-attack results are replaced by [entries]. */
    private fun answerWithPromptAttack(entries: List<GuardrailChecksPromptAttackResultEntry>): InvokeGuardrailChecksResponse {
        val complete = answer()
        return complete.toBuilder()
            .results(
                complete.results().toBuilder()
                    .promptAttack(GuardrailChecksPromptAttackResult.builder().results(entries).build())
                    .build(),
            )
            .build()
    }

    private fun promptAttackEntry(category: String, score: Double?): GuardrailChecksPromptAttackResultEntry =
        GuardrailChecksPromptAttackResultEntry.builder().category(category).severityScore(score).build()

    private fun stubAnswer(answer: InvokeGuardrailChecksResponse) {
        whenever(bedrockRuntimeClient.invokeGuardrailChecks(any<InvokeGuardrailChecksRequest>())).thenReturn(answer)
    }

    @Test
    fun testCheck_BenignPasses() {
        stubAnswer(answer())

        val result = service.check(AskMode.CHAT, MESSAGE)

        assertEquals(GuardrailVerdict.Passed, result.verdict)
        assertEquals(GuardrailOutcome.PASSED, result.outcome)
        assertFalse(result.isBlocked)
        // Every checked category is kept for the admin view.
        assertEquals((CONTENT_CATEGORIES + PROMPT_ATTACK_CATEGORIES).toSet(), result.scores.keys)
    }

    @Test
    fun testCheck_PromptAttackBlocks() {
        // Measured on a Finnish injection attempt: labelled jailbreak, not injection.
        stubAnswer(answer(mapOf("JAILBREAK" to 1.0, "PROMPT_INJECTION" to 0.0)))

        val result = service.check(AskMode.CHAT, MESSAGE)

        assertEquals(GuardrailVerdict.Blocked(listOf("JAILBREAK")), result.verdict)
        assertEquals(GuardrailOutcome.BLOCKED, result.outcome)
    }

    @Test
    fun testCheck_ContentFilterBlocks() {
        stubAnswer(answer(mapOf("HATE" to 0.6)))

        assertEquals(GuardrailVerdict.Blocked(listOf("HATE")), service.check(AskMode.CHAT, MESSAGE).verdict)
    }

    @Test
    fun testCheck_ScoreBelowThresholdPasses() {
        stubAnswer(answer(mapOf("PROMPT_INJECTION" to 0.4, "INSULTS" to 0.4)))

        assertEquals(GuardrailVerdict.Passed, service.check(AskMode.CHAT, MESSAGE).verdict)
    }

    @Test
    fun testCheck_FitThresholdHigherThanChat() {
        // 0.6 reaches the chat threshold (0.6) but not the job-description one (0.8).
        stubAnswer(answer(mapOf("PROMPT_INJECTION" to 0.6)))

        assertTrue(service.check(AskMode.CHAT, MESSAGE).isBlocked)
        assertFalse(service.check(AskMode.FIT_CHECK, MESSAGE).isBlocked)
    }

    @Test
    fun testCheck_FitThresholdReachedBlocks() {
        stubAnswer(answer(mapOf("PROMPT_INJECTION" to 0.8)))

        assertTrue(service.check(AskMode.FIT_CHECK, MESSAGE).isBlocked)
    }

    @Test
    fun testCheck_SumsTextUnits() {
        // A 3,500-character job description: 4 units per check, billed separately.
        stubAnswer(answer(units = 4))

        val result = service.check(AskMode.FIT_CHECK, MESSAGE)

        assertEquals(4, result.contentFilterTextUnits)
        assertEquals(4, result.promptAttackTextUnits)
        assertEquals(8, result.textUnits)
    }

    @Test
    fun testCheck_MissingUsageEstimatesTextUnits() {
        stubAnswer(answer(units = null))

        val result = service.check(AskMode.FIT_CHECK, "x".repeat(2001))

        // 2,001 characters start 3 units of 1,000 per check: never recorded as free.
        assertEquals(3, result.contentFilterTextUnits)
        assertEquals(3, result.promptAttackTextUnits)
    }

    @Test
    fun testCheck_SendsBothChecksWithAllCategories() {
        stubAnswer(answer())

        service.check(AskMode.CHAT, MESSAGE)

        val captor = argumentCaptor<InvokeGuardrailChecksRequest>()
        verify(bedrockRuntimeClient).invokeGuardrailChecks(captor.capture())
        val request = captor.firstValue
        val message = request.messages().single()
        assertEquals(GuardrailChecksRole.USER, message.role())
        assertEquals(MESSAGE, message.content().single().text())
        assertEquals(CONTENT_CATEGORIES, request.checks().contentFilter().categories().map { it.categoryAsString() })
        assertEquals(PROMPT_ATTACK_CATEGORIES, request.checks().promptAttack().categories().map { it.categoryAsString() })
        assertEquals(Duration.ofMillis(10000), request.overrideConfiguration().get().apiCallTimeout().get())
    }

    @Test
    fun testCheck_SdkErrorFailsClosed() {
        whenever(bedrockRuntimeClient.invokeGuardrailChecks(any<InvokeGuardrailChecksRequest>()))
            .thenThrow(SdkClientException.create("Unable to execute HTTP request: connect timed out"))

        assertThrows<GuardrailUnavailableException> { service.check(AskMode.CHAT, MESSAGE) }
    }

    @Test
    fun testCheck_AccessDeniedFailsClosed() {
        whenever(bedrockRuntimeClient.invokeGuardrailChecks(any<InvokeGuardrailChecksRequest>()))
            .thenThrow(AccessDeniedException.builder().message("not authorized").build())

        val error = assertThrows<GuardrailUnavailableException> { service.check(AskMode.CHAT, MESSAGE) }

        assertTrue(error.message.orEmpty().contains("bedrock:InvokeGuardrailChecks"), error.message)
    }

    @Test
    fun testCheck_MissingCheckResultFailsClosed() {
        // The prompt-attack part is missing: the input can't be shown to be safe.
        stubAnswer(
            InvokeGuardrailChecksResponse.builder()
                .results(GuardrailChecksResults.builder().contentFilter(answer().results().contentFilter()).build())
                .build(),
        )

        assertThrows<GuardrailUnavailableException> { service.check(AskMode.CHAT, MESSAGE) }
    }

    @Test
    fun testCheck_MissingResultsFailsClosed() {
        stubAnswer(InvokeGuardrailChecksResponse.builder().usage(answer().usage()).build())

        assertThrows<GuardrailUnavailableException> { service.check(AskMode.CHAT, MESSAGE) }
    }

    @Test
    fun testCheck_MissingScoreFailsClosed() {
        stubAnswer(
            answerWithPromptAttack(
                listOf(
                    promptAttackEntry("JAILBREAK", null),
                    promptAttackEntry("PROMPT_INJECTION", 0.0),
                    promptAttackEntry("PROMPT_LEAKAGE", 0.0),
                ),
            ),
        )

        assertThrows<GuardrailUnavailableException> { service.check(AskMode.CHAT, MESSAGE) }
    }

    @Test
    fun testCheck_MissingCategoryFailsClosed() {
        // JAILBREAK is left out and the rest is 0.0: without the check, the input would pass.
        stubAnswer(
            answerWithPromptAttack(
                listOf(promptAttackEntry("PROMPT_INJECTION", 0.0), promptAttackEntry("PROMPT_LEAKAGE", 0.0)),
            ),
        )

        val error = assertThrows<GuardrailUnavailableException> { service.check(AskMode.CHAT, MESSAGE) }

        assertTrue(error.message.orEmpty().contains("JAILBREAK"), error.message)
    }

    @Test
    fun testCheck_ExtraCategoryKept() {
        // A category Bedrock may add later still counts towards the verdict.
        stubAnswer(
            answerWithPromptAttack(
                PROMPT_ATTACK_CATEGORIES.map { promptAttackEntry(it, 0.0) } + promptAttackEntry("NEW_ATTACK", 1.0),
            ),
        )

        val result = service.check(AskMode.CHAT, MESSAGE)

        assertEquals(GuardrailVerdict.Blocked(listOf("NEW_ATTACK")), result.verdict)
    }

    @Test
    fun testBlockedBy_StoredScoresUseTheModeThresholds() {
        // Scores as a turn stores them: both checks in one map.
        val scores = mapOf("HATE" to 0.6, "INSULTS" to 0.2, "PROMPT_INJECTION" to 0.6, "JAILBREAK" to 0.0)
        val settings = testAskProperties().guardrail

        assertEquals(listOf("HATE", "PROMPT_INJECTION"), BedrockGuardrailService.blockedBy(settings, AskMode.CHAT, scores))
        assertEquals(
            listOf("HATE"),
            BedrockGuardrailService.blockedBy(settings, AskMode.FIT_CHECK, scores),
            "0.6 is below the job-description prompt-attack threshold (0.8), not below the content one",
        )
    }

    @Test
    fun testBlockedBy_StoredScoresMatchTheCheck() {
        // The admin view must name the same categories the check blocked on.
        stubAnswer(answer(mapOf("HATE" to 0.6, "JAILBREAK" to 1.0)))

        val result = service.check(AskMode.CHAT, MESSAGE)

        assertEquals(
            (result.verdict as GuardrailVerdict.Blocked).categories,
            BedrockGuardrailService.blockedBy(testAskProperties().guardrail, AskMode.CHAT, result.scores),
        )
    }

    @Test
    fun testCheck_BlankTextRejectedWithoutCalling() {
        assertThrows<IllegalArgumentException> { service.check(AskMode.CHAT, " ") }

        verifyNoInteractions(bedrockRuntimeClient)
    }

    // The two tests below use a real BedrockRuntimeClient against a local fake Bedrock, so the
    // SDK's own JSON handling and retry behaviour are tested too.

    /**
     * Runs [test] with a real client whose requests go to a local server that answers every
     * request with [status] and [body]. Returns how many requests the server received.
     */
    private fun withFakeBedrock(status: Int, body: String, test: (BedrockGuardrailService, AtomicReference<String>) -> Unit): Int {
        val requests = AtomicInteger()
        val lastRequestBody = AtomicReference("")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.incrementAndGet()
            lastRequestBody.set(exchange.requestBody.readAllBytes().decodeToString())
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client = BedrockRuntimeClient.builder()
                .region(Region.EU_NORTH_1)
                .endpointOverride(URI("http://127.0.0.1:${server.address.port}"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test-key", "test-secret")))
                .httpClient(UrlConnectionHttpClient.create())
                .build()
            client.use { test(BedrockGuardrailService(it, testAskProperties()), lastRequestBody) }
        } finally {
            server.stop(0)
        }
        return requests.get()
    }

    @Test
    fun testCheck_ParsesRealAnswer() {
        withFakeBedrock(200, REAL_JAILBREAK_ANSWER) { service, requestBody ->
            val result = service.check(AskMode.CHAT, MESSAGE)

            assertEquals(GuardrailVerdict.Blocked(listOf("MISCONDUCT", "JAILBREAK")), result.verdict)
            assertEquals(2, result.textUnits)
            // The request has the shape that was tested against the real API.
            assertTrue(requestBody.get().contains(""""role":"user""""), requestBody.get())
            assertTrue(requestBody.get().contains(""""category":"PROMPT_LEAKAGE""""), requestBody.get())
        }
    }

    @Test
    fun testCheck_ServerErrorNotRetried() {
        val requests = withFakeBedrock(500, """{"message":"Internal server error"}""") { service, _ ->
            assertThrows<GuardrailUnavailableException> { service.check(AskMode.CHAT, MESSAGE) }
        }

        // Without the no-retry setting, the SDK sends this request 4 times.
        assertEquals(1, requests)
    }
}