package me.longng.finnish_learning_backend.service

import me.longng.finnish_learning_backend.controller.dto.EssayCefrLevel
import me.longng.finnish_learning_backend.controller.dto.EssayIssue
import me.longng.finnish_learning_backend.controller.dto.EssayIssueKind
import me.longng.finnish_learning_backend.controller.dto.EvaluateEssayResponse
import me.longng.finnish_learning_backend.domain.EssayTopic
import me.longng.finnish_learning_backend.persistence.EssayTopicRepository
import me.longng.finnish_learning_backend.service.bedrock.BedrockEssayClient
import me.longng.finnish_learning_backend.service.bedrock.EssayEvaluationQuotaExceededException
import me.longng.finnish_learning_backend.service.bedrock.EssayEvaluationUpstreamException
import me.longng.finnish_learning_backend.service.bedrock.EssayQuotaTracker
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EssayEvaluationServiceTest {

    private val bedrockEssayClient: BedrockEssayClient = mock()
    private val essayTopicRepository: EssayTopicRepository = mock()
    private val quotaTracker: EssayQuotaTracker = mock()

    private val service = EssayEvaluationService(
        bedrockEssayClient = bedrockEssayClient,
        essayTopicRepository = essayTopicRepository,
        quotaTracker = quotaTracker,
        dailyQuota = DAILY_QUOTA,
    )

    /** Stubs the happy path: prompt exists, quota available, client answers. */
    private fun stubHappyPath() {
        whenever(essayTopicRepository.findById(PROMPT_ID)).thenReturn(PROMPT)
        whenever(quotaTracker.tryConsume(USER_ID, DAILY_QUOTA)).thenReturn(true)
        whenever(bedrockEssayClient.evaluate(any(), any())).thenReturn(RESPONSE)
    }

    @Test
    fun testGetPrompts_MapsToResponse() {
        whenever(essayTopicRepository.findByTopicId(TOPIC_ID)).thenReturn(
            listOf(PROMPT, EssayTopic(id = 8, topicId = TOPIC_ID, title = "Toinen aihe")),
        )

        val prompts = service.getPrompts(TOPIC_ID)

        assertEquals(2, prompts.size)
        assertEquals(PROMPT_ID, prompts[0].id)
        assertEquals(PROMPT_TITLE, prompts[0].title)
        assertEquals(8, prompts[1].id)
    }

    @Test
    fun testGetPrompts_UnknownTopic() {
        whenever(essayTopicRepository.findByTopicId(99999)).thenReturn(emptyList())

        assertTrue(service.getPrompts(99999).isEmpty())
    }

    @Test
    fun testEvaluate_Success() {
        stubHappyPath()

        val result = service.evaluate(USER_ID, PROMPT_ID, VALID_ESSAY)

        assertEquals(RESPONSE, result)
        verify(bedrockEssayClient).evaluate(PROMPT_TITLE, VALID_ESSAY)
        verify(quotaTracker, times(1)).tryConsume(USER_ID, DAILY_QUOTA)
    }

    @Test
    fun testEvaluate_BlankEssay() {
        assertThrows<IllegalArgumentException> {
            service.evaluate(USER_ID, PROMPT_ID, "   ")
        }

        verify(quotaTracker, never()).tryConsume(any(), any())
        verify(bedrockEssayClient, never()).evaluate(any(), any())
    }

    @Test
    fun testEvaluate_TooShort() {
        assertThrows<IllegalArgumentException> {
            service.evaluate(USER_ID, PROMPT_ID, "a".repeat(299))
        }

        verify(bedrockEssayClient, never()).evaluate(any(), any())
    }

    @Test
    fun testEvaluate_TooLong() {
        assertThrows<IllegalArgumentException> {
            service.evaluate(USER_ID, PROMPT_ID, "a".repeat(2_501))
        }

        verify(bedrockEssayClient, never()).evaluate(any(), any())
    }

    @Test
    fun testEvaluate_BoundaryLengths() {
        stubHappyPath()

        // Both ends of the accepted band must pass — this is the off-by-one guard.
        service.evaluate(USER_ID, PROMPT_ID, "a".repeat(300))
        service.evaluate(USER_ID, PROMPT_ID, "a".repeat(2_500))

        verify(bedrockEssayClient, times(2)).evaluate(any(), any())
    }

    @Test
    fun testEvaluate_UnknownPrompt() {
        whenever(essayTopicRepository.findById(PROMPT_ID)).thenReturn(null)

        assertThrows<IllegalArgumentException> {
            service.evaluate(USER_ID, PROMPT_ID, VALID_ESSAY)
        }

        // A prompt the caller got wrong must not cost them a quota unit.
        verify(quotaTracker, never()).tryConsume(any(), any())
        verify(bedrockEssayClient, never()).evaluate(any(), any())
    }

    @Test
    fun testEvaluate_QuotaExceeded() {
        whenever(essayTopicRepository.findById(PROMPT_ID)).thenReturn(PROMPT)
        whenever(quotaTracker.tryConsume(USER_ID, DAILY_QUOTA)).thenReturn(false)

        val ex = assertThrows<EssayEvaluationQuotaExceededException> {
            service.evaluate(USER_ID, PROMPT_ID, VALID_ESSAY)
        }

        assertEquals(USER_ID, ex.userId)
        assertEquals(DAILY_QUOTA, ex.dailyLimit)
        // A rate-limited user never triggers a billable Bedrock request.
        verify(bedrockEssayClient, never()).evaluate(any(), any())
    }

    @Test
    fun testEvaluate_UpstreamFailure() {
        whenever(essayTopicRepository.findById(PROMPT_ID)).thenReturn(PROMPT)
        whenever(quotaTracker.tryConsume(USER_ID, DAILY_QUOTA)).thenReturn(true)
        whenever(bedrockEssayClient.evaluate(any(), any()))
            .thenThrow(EssayEvaluationUpstreamException("Bedrock upstream error"))

        // The service adds nothing to upstream failures — GlobalExceptionHandler maps them.
        assertThrows<EssayEvaluationUpstreamException> {
            service.evaluate(USER_ID, PROMPT_ID, VALID_ESSAY)
        }
    }

    companion object {
        private const val USER_ID = 7
        private const val TOPIC_ID = 3
        private const val PROMPT_ID = 42
        private const val DAILY_QUOTA = 20
        private const val PROMPT_TITLE = "Menet kahvilaan, mutta kaikki pöydät ovat likaisia."

        private val PROMPT = EssayTopic(id = PROMPT_ID, topicId = TOPIC_ID, title = PROMPT_TITLE)

        private val VALID_ESSAY = "Minä menin eilen kahvilaan. ".repeat(15)

        private val RESPONSE = EvaluateEssayResponse(
            cefrLevel = EssayCefrLevel.B1_1,
            onTopic = true,
            issues = listOf(
                EssayIssue(
                    kind = EssayIssueKind.TYPO,
                    original = "kirjastoo",
                    suggestion = "kirjastoon",
                ),
            ),
            feedback = "Good structure.",
        )
    }
}