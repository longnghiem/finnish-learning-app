package me.longng.finnish_learning_backend.config

import java.math.BigDecimal

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
    session: AskProperties.Session = AskProperties.Session(ttlMinutes = 60, maxMessages = 10, maxMessageChars = 500),
    guardrail: AskProperties.Guardrail = AskProperties.Guardrail(
        contentThreshold = 0.6,
        chatPromptAttackThreshold = 0.6,
        fitPromptAttackThreshold = 0.8,
        timeoutMs = 10000,
    ),
    agent: AskProperties.Agent = AskProperties.Agent(
        maxReadsPerRound = 4,
        chatMaxTokens = 400,
        chatMaxToolRounds = 3,
        fitStepMaxTokens = 300,
        fitReportMaxTokens = 1500,
        fitMaxToolRounds = 6,
    ),
    budget: AskProperties.Budget = AskProperties.Budget(
        monthlyUsd = BigDecimal("5.00"),
        dailyUsd = BigDecimal("0.25"),
        toolResultTokens = 1000,
    ),
    price: AskProperties.Price = AskProperties.Price(
        inputPerMillionTokens = BigDecimal("1.10"),
        outputPerMillionTokens = BigDecimal("5.50"),
        cacheReadPerMillionTokens = BigDecimal("0.11"),
        cacheWritePerMillionTokens = BigDecimal("1.375"),
        guardrailContentPerKUnits = BigDecimal("0.07"),
        guardrailPromptAttackPerKUnits = BigDecimal("0.08"),
    ),
    mcp: AskProperties.Mcp = AskProperties.Mcp(
        url = "http://localhost:3001/mcp",
        region = "eu-north-1",
        signRequests = true,
        connectTimeoutMs = 3000,
        requestTimeoutMs = 10000,
    ),
    model: AskProperties.Model = AskProperties.Model(
        id = "eu.anthropic.claude-haiku-4-5-20251001-v1:0",
        additionalRequestFields = "",
        timeoutMs = 30000,
    ),
    fitCheck: AskProperties.FitCheck = AskProperties.FitCheck(maxChars = 6000, reportTimeoutMs = 120000),
) = AskProperties(
    ipHashKey = ipHashKey,
    limits = limits,
    turnstile = turnstile,
    session = session,
    guardrail = guardrail,
    agent = agent,
    budget = budget,
    price = price,
    mcp = mcp,
    model = model,
    fitCheck = fitCheck,
)