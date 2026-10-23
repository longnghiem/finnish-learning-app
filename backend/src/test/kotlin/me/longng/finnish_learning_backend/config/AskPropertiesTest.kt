package me.longng.finnish_learning_backend.config

import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
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
        }
    }

    @Test
    fun testBind_ZeroLimitFails() {
        runner.withPropertyValues("app.ask.limits.fit-checks-per-ip-per-day=0").run { context ->
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