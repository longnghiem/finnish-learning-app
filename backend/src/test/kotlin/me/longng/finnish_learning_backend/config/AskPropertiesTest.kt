package me.longng.finnish_learning_backend.config

import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class AskPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(AskProperties::class)
    class Config

    /** A Spring context with only [AskProperties] in it, bound from the given properties. */
    private val runner = ApplicationContextRunner()
        .withUserConfiguration(Config::class.java)
        .withPropertyValues(
            "app.ask.ip-hash-key=secret-value-that-must-not-be-printed",
            "app.ask.limits.chat-per-ip-per-day=30",
            "app.ask.limits.fit-checks-per-ip-per-day=3",
            "app.ask.limits.sessions-per-ip-per-hour=5",
            "app.ask.turnstile.secret-key=turnstile-secret-that-must-not-be-printed",
            "app.ask.turnstile.verify-url=https://turnstile.test/siteverify",
            "app.ask.turnstile.connect-timeout-ms=3000",
            "app.ask.turnstile.read-timeout-ms=5000",
            "app.ask.session.ttl-minutes=60",
            "app.ask.session.max-messages=10",
            "app.ask.guardrail.content-threshold=0.6",
            "app.ask.guardrail.chat-prompt-attack-threshold=0.6",
            "app.ask.guardrail.fit-prompt-attack-threshold=0.8",
            "app.ask.guardrail.timeout-ms=10000",
            "app.ask.agent.max-reads-per-round=4",
            "app.ask.agent.chat-max-tokens=400",
            "app.ask.agent.chat-max-tool-rounds=3",
            "app.ask.agent.fit-step-max-tokens=300",
            "app.ask.agent.fit-report-max-tokens=1500",
            "app.ask.agent.fit-max-tool-rounds=6",
            "app.ask.budget.monthly-usd=5.00",
            "app.ask.budget.daily-usd=0.25",
            "app.ask.budget.tool-result-tokens=1000",
            "app.ask.price.input-per-million-tokens=1.10",
            "app.ask.price.output-per-million-tokens=5.50",
            "app.ask.price.cache-read-per-million-tokens=0.11",
            "app.ask.price.cache-write-per-million-tokens=1.375",
            "app.ask.price.guardrail-content-per-k-units=0.07",
            "app.ask.price.guardrail-prompt-attack-per-k-units=0.08",
        )

    @Test
    fun testBind_FromProperties() {
        runner.run { context ->
            val properties = context.getBean(AskProperties::class.java)

            assertEquals("secret-value-that-must-not-be-printed", properties.ipHashKey)
            assertEquals(AskProperties.Limits(chatPerIpPerDay = 30, fitChecksPerIpPerDay = 3, sessionsPerIpPerHour = 5), properties.limits)
            assertEquals(
                AskProperties.Turnstile(
                    secretKey = "turnstile-secret-that-must-not-be-printed",
                    verifyUrl = "https://turnstile.test/siteverify",
                    connectTimeoutMs = 3000,
                    readTimeoutMs = 5000,
                ),
                properties.turnstile,
            )
            assertEquals(AskProperties.Session(ttlMinutes = 60, maxMessages = 10), properties.session)
            assertEquals(
                AskProperties.Guardrail(
                    contentThreshold = 0.6,
                    chatPromptAttackThreshold = 0.6,
                    fitPromptAttackThreshold = 0.8,
                    timeoutMs = 10000,
                ),
                properties.guardrail,
            )
            assertEquals(
                AskProperties.Agent(
                    maxReadsPerRound = 4,
                    chatMaxTokens = 400,
                    chatMaxToolRounds = 3,
                    fitStepMaxTokens = 300,
                    fitReportMaxTokens = 1500,
                    fitMaxToolRounds = 6,
                ),
                properties.agent,
            )
            assertEquals(
                AskProperties.Budget(monthlyUsd = BigDecimal("5.00"), dailyUsd = BigDecimal("0.25"), toolResultTokens = 1000),
                properties.budget,
            )
            assertEquals(
                AskProperties.Price(
                    inputPerMillionTokens = BigDecimal("1.10"),
                    outputPerMillionTokens = BigDecimal("5.50"),
                    cacheReadPerMillionTokens = BigDecimal("0.11"),
                    cacheWritePerMillionTokens = BigDecimal("1.375"),
                    guardrailContentPerKUnits = BigDecimal("0.07"),
                    guardrailPromptAttackPerKUnits = BigDecimal("0.08"),
                ),
                properties.price,
            )
        }
    }

    @Test
    fun testBind_ZeroLimitFails() {
        runner.withPropertyValues("app.ask.limits.fit-checks-per-ip-per-day=0").run { context ->
            assertNotNull(context.startupFailure)
        }
    }

    @Test
    fun testBind_ZeroGuardrailThresholdFails() {
        // At 0.0 every input would be blocked, because every score is at least 0.0.
        runner.withPropertyValues("app.ask.guardrail.chat-prompt-attack-threshold=0.0").run { context ->
            assertNotNull(context.startupFailure)
        }
    }

    @Test
    fun testBind_GuardrailThresholdAboveOneFails() {
        // No score is above 1.0, so nothing would ever be blocked.
        runner.withPropertyValues("app.ask.guardrail.content-threshold=1.2").run { context ->
            assertNotNull(context.startupFailure)
        }
    }

    @Test
    fun testBind_ZeroBudgetFails() {
        // A zero cap would refuse every request without saying why.
        runner.withPropertyValues("app.ask.budget.daily-usd=0").run { context ->
            assertNotNull(context.startupFailure)
        }
    }

    @Test
    fun testBind_ZeroPriceFails() {
        // A zero price would record every call as free, so the caps would never be reached.
        runner.withPropertyValues("app.ask.price.output-per-million-tokens=0").run { context ->
            assertNotNull(context.startupFailure)
        }
    }

    @Test
    fun testBind_BlankTurnstileSecretAllowed() {
        // The app must start without a Turnstile secret; only the chatbot needs it.
        runner.withPropertyValues("app.ask.turnstile.secret-key=").run { context ->
            assertEquals("", context.getBean(AskProperties::class.java).turnstile.secretKey)
        }
    }

    @Test
    fun testToString_HidesSecrets() {
        runner.run { context ->
            val text = context.getBean(AskProperties::class.java).toString()

            assertFalse(text.contains("secret-value"), text)
            assertFalse(text.contains("turnstile-secret"), text)
        }
    }
}