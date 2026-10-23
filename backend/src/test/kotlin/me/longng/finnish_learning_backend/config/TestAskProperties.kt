package me.longng.finnish_learning_backend.config

/**
 * An [AskProperties] for unit tests, with the same values as `application.properties`. Each test
 * overrides only what it is about, so adding a setting later changes this one place.
 */
fun testAskProperties(
    ipHashKey: String = "test-only-ip-hash-key-not-a-secret-0123456789",
    limits: AskProperties.Limits = AskProperties.Limits(
        chatPerIpPerDay = 30,
        fitChecksPerIpPerDay = 3,
        sessionsPerIpPerHour = 5,
    ),
    turnstile: AskProperties.Turnstile = AskProperties.Turnstile(
        secretKey = "test-only-turnstile-secret",
        verifyUrl = "https://turnstile.test/siteverify",
        connectTimeoutMs = 3000,
        readTimeoutMs = 5000,
    ),
    session: AskProperties.Session = AskProperties.Session(ttlMinutes = 60, maxMessages = 10),
    guardrail: AskProperties.Guardrail = AskProperties.Guardrail(
        contentThreshold = 0.6,
        chatPromptAttackThreshold = 0.6,
        fitPromptAttackThreshold = 0.8,
        timeoutMs = 10000,
    ),
) = AskProperties(
    ipHashKey = ipHashKey,
    limits = limits,
    turnstile = turnstile,
    session = session,
    guardrail = guardrail,
)