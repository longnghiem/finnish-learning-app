package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.config.AskProperties
import me.longng.finnish_learning_backend.domain.ask.AskMode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy
import software.amazon.awssdk.core.SdkPlugin
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailChecksConfig
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailChecksContentBlock
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailChecksContentFilterCategory
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailChecksContentFilterCategoryConfig
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailChecksContentFilterConfig
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailChecksMessage
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailChecksPromptAttackCategory
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailChecksPromptAttackCategoryConfig
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailChecksPromptAttackConfig
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailChecksRole
import software.amazon.awssdk.services.bedrockruntime.model.InvokeGuardrailChecksRequest
import software.amazon.awssdk.services.bedrockruntime.model.InvokeGuardrailChecksResponse
import java.time.Duration

/**
 * [GuardrailService] on Bedrock's `InvokeGuardrailChecks` API.
 *
 * The API needs no guardrail resource in the AWS account: every request says which checks to run.
 * It only **detects**, it never blocks: each category gets a score from 0.0 to 1.0, and this class
 * compares the scores with [AskProperties.Guardrail]. Two checks run in one call:
 * - **content filter**: violence, hate, sexual, misconduct, insults;
 * - **prompt attack**: jailbreak, prompt injection, prompt leakage.
 *
 * **Any** prompt-attack category at or above the threshold blocks, never one chosen category:
 * measured on Finnish samples, an injection attempt scored 0.0 as `PROMPT_INJECTION` but 1.0 as
 * `JAILBREAK`. The labels are unreliable; the highest score is what counts.
 *
 * **Fail-closed, no retry.** Any failure (network, timeout, throttling, missing permission, an
 * incomplete answer) throws [GuardrailUnavailableException], and the caller refuses the input. The
 * SDK's automatic retries are switched off for this call: a recruiter gets a quick error instead
 * of a long wait, and repeated attack traffic can't multiply the calls. The call is limited to
 * [AskProperties.Guardrail.timeoutMs], so the shared client's long read timeout (meant for the
 * essay feature) doesn't apply.
 *
 * Logs the mode, the outcome, the blocking categories, the highest score, the text units and the
 * latency. Never the text.
 */
@Service
class BedrockGuardrailService(
    private val bedrockRuntimeClient: BedrockRuntimeClient,
    properties: AskProperties,
) : GuardrailService {
    private val logger = LoggerFactory.getLogger(BedrockGuardrailService::class.java)

    private val settings = properties.guardrail

    override fun check(mode: AskMode, text: String): GuardrailCheckResult {
        require(text.isNotBlank()) { "text must not be blank" }

        val startedAt = System.nanoTime()
        val response = callGuardrail(mode, text)
        val result = evaluate(mode, response, text.length)
        logger.info(
            "Guardrail check: mode={} outcome={} blockedBy={} maxScore={} textUnits={} latencyMs={}",
            mode,
            result.outcome,
            (result.verdict as? GuardrailVerdict.Blocked)?.categories ?: emptyList<String>(),
            result.scores.values.max(),
            result.textUnits,
            (System.nanoTime() - startedAt) / 1_000_000,
        )
        return result
    }

    private fun callGuardrail(mode: AskMode, text: String): InvokeGuardrailChecksResponse =
        try {
            bedrockRuntimeClient.invokeGuardrailChecks(buildRequest(text))
        } catch (ex: AccessDeniedException) {
            logger.error("Bedrock denied the guardrail check: mode={}", mode, ex)
            throw GuardrailUnavailableException(
                "Bedrock denied InvokeGuardrailChecks. The IAM role (or the local IAM user) is " +
                        "probably missing the bedrock:InvokeGuardrailChecks permission.",
                ex,
            )
        } catch (ex: SdkException) {
            logger.error("Guardrail check failed: mode={}", mode, ex)
            throw GuardrailUnavailableException("Guardrail check failed: ${ex.message}", ex)
        }

    private fun buildRequest(text: String): InvokeGuardrailChecksRequest =
        InvokeGuardrailChecksRequest.builder()
            .messages(
                GuardrailChecksMessage.builder()
                    .role(GuardrailChecksRole.USER)
                    .content(GuardrailChecksContentBlock.fromText(text))
                    .build(),
            )
            .checks(CHECKS)
            .overrideConfiguration { config ->
                config.apiCallTimeout(Duration.ofMillis(settings.timeoutMs))
                    .addPlugin(NO_RETRY)
            }
            .build()

    /**
     * Turns the scores into a verdict. A missing check, or a requested category without a score,
     * fails closed: the input can't be shown to be safe.
     */
    private fun evaluate(mode: AskMode, response: InvokeGuardrailChecksResponse, textLength: Int): GuardrailCheckResult {
        val results = response.results()
            ?: throw GuardrailUnavailableException("Guardrail answer has no results")
        val contentScores = scoresOf(
            "content filter",
            CONTENT_FILTER_CATEGORIES.map { it.toString() },
            results.contentFilter()?.results()?.map { it.categoryAsString() to it.severityScore() },
        )
        val promptAttackScores = scoresOf(
            "prompt attack",
            PROMPT_ATTACK_CATEGORIES.map { it.toString() },
            results.promptAttack()?.results()?.map { it.categoryAsString() to it.severityScore() },
        )

        val promptAttackThreshold = when (mode) {
            AskMode.CHAT -> settings.chatPromptAttackThreshold
            AskMode.FIT_CHECK -> settings.fitPromptAttackThreshold
        }
        val blockedBy = contentScores.filterValues { it >= settings.contentThreshold }.keys +
                promptAttackScores.filterValues { it >= promptAttackThreshold }.keys

        val usage = response.usage()
        return GuardrailCheckResult(
            verdict = if (blockedBy.isEmpty()) GuardrailVerdict.Passed else GuardrailVerdict.Blocked(blockedBy.toList()),
            scores = contentScores + promptAttackScores,
            contentFilterTextUnits = usage?.contentFilter()?.textUnits() ?: estimatedTextUnits(textLength),
            promptAttackTextUnits = usage?.promptAttack()?.textUnits() ?: estimatedTextUnits(textLength),
        )
    }

    /**
     * The scores of one check, by category. Every [requested] category must have a score: a
     * category left out of the answer would otherwise never be compared with its threshold.
     * Extra categories are kept, so a category Bedrock adds later still counts.
     */
    private fun scoresOf(
        check: String,
        requested: List<String>,
        entries: List<Pair<String, Double?>>?,
    ): Map<String, Double> {
        val scores = entries.orEmpty().associate { (category, score) ->
            category to (score ?: throw GuardrailUnavailableException("Guardrail answer has no score for $category"))
        }
        val missing = requested - scores.keys
        if (missing.isNotEmpty()) {
            throw GuardrailUnavailableException("Guardrail answer has no $check score for $missing")
        }
        return scores
    }

    /**
     * The units Bedrock bills when the answer doesn't say: one per started 1,000 characters, per
     * check. Used so a missing usage block never records the check as free.
     */
    private fun estimatedTextUnits(textLength: Int): Int = (textLength + CHARS_PER_TEXT_UNIT - 1) / CHARS_PER_TEXT_UNIT

    companion object {
        private const val CHARS_PER_TEXT_UNIT = 1000

        private val CONTENT_FILTER_CATEGORIES = listOf(
            GuardrailChecksContentFilterCategory.VIOLENCE,
            GuardrailChecksContentFilterCategory.HATE,
            GuardrailChecksContentFilterCategory.SEXUAL,
            GuardrailChecksContentFilterCategory.MISCONDUCT,
            GuardrailChecksContentFilterCategory.INSULTS,
        )

        private val PROMPT_ATTACK_CATEGORIES = listOf(
            GuardrailChecksPromptAttackCategory.JAILBREAK,
            GuardrailChecksPromptAttackCategory.PROMPT_INJECTION,
            GuardrailChecksPromptAttackCategory.PROMPT_LEAKAGE,
        )

        /** Both checks with every category, built once: the request part that never changes. */
        private val CHECKS: GuardrailChecksConfig = GuardrailChecksConfig.builder()
            .contentFilter(
                GuardrailChecksContentFilterConfig.builder()
                    .categories(
                        CONTENT_FILTER_CATEGORIES.map {
                            GuardrailChecksContentFilterCategoryConfig.builder().category(it).build()
                        },
                    )
                    .build(),
            )
            .promptAttack(
                GuardrailChecksPromptAttackConfig.builder()
                    .categories(
                        PROMPT_ATTACK_CATEGORIES.map {
                            GuardrailChecksPromptAttackCategoryConfig.builder().category(it).build()
                        },
                    )
                    .build(),
            )
            .build()

        /** Switches off the SDK's automatic retries for one request (see the class comment). */
        private val NO_RETRY = SdkPlugin { config ->
            config.overrideConfiguration { it.retryStrategy(AwsRetryStrategy.doNotRetry()) }
        }
    }
}