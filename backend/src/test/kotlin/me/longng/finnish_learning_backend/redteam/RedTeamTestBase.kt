package me.longng.finnish_learning_backend.redteam

import me.longng.finnish_learning_backend.TestcontainersConfiguration
import me.longng.finnish_learning_backend.config.AskProperties
import me.longng.finnish_learning_backend.domain.ask.AskSession
import me.longng.finnish_learning_backend.domain.ask.AskTurn
import me.longng.finnish_learning_backend.persistence.AskSessionRepository
import me.longng.finnish_learning_backend.persistence.AskTurnRepository
import me.longng.finnish_learning_backend.persistence.randomIpHash
import me.longng.finnish_learning_backend.service.ask.BedrockGuardrailService
import me.longng.finnish_learning_backend.service.ask.mcp.McpProfileClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * What both red-team classes share: the whole app in a Spring context, with the real Bedrock model
 * and guardrail, the profile server of `npm run dev` and a throwaway PostgreSQL. Both classes use
 * the same settings, so Spring starts the context once for the whole run.
 *
 * Tagged `redteam`: `./gradlew test` leaves it out, `./gradlew redTeam` runs only it.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
@TestPropertySource(locations = ["classpath:redteam/redteam.properties"])
@Tag("redteam")
abstract class RedTeamTestBase {

    @Autowired
    protected lateinit var sessionRepository: AskSessionRepository

    @Autowired
    protected lateinit var turnRepository: AskTurnRepository

    @Autowired
    protected lateinit var profileClient: McpProfileClient

    @Autowired
    protected lateinit var properties: AskProperties

    /**
     * The cases are written for the example profile (its skills, its "not done" list). Against
     * another profile they would fail for the wrong reasons, so the run stops at once.
     */
    @BeforeEach
    fun requireExampleProfile() {
        check(profileClient.readIndex().contains(EXAMPLE_PROFILE_MARK)) {
            "The red team must run against the example profile: build the index from mcp-server/profile.example " +
                    "(see src/test/resources/redteam/README.md)"
        }
    }

    /**
     * A new session for one case, as if from a new visitor: its random IP hash keeps the per-IP
     * limits (3 fit checks a day) out of the way. It is stored directly, without the bot check.
     */
    protected fun newSession(): AskSession =
        sessionRepository.create(UUID.randomUUID(), randomIpHash(), Instant.now().plus(Duration.ofHours(1)))

    /** The one turn [session] has after its case ran. */
    protected fun turnOf(session: AskSession): AskTurn = turnRepository.findBySession(session.id).single()

    /**
     * The summary line of a case that failed with something other than a block, e.g. Bedrock
     * unreachable or refusing the model. Its turn may be missing (if the failure came before it
     * was stored), so cost and scores are taken from it only if it exists.
     */
    protected fun errorResultOf(id: String, session: AskSession, details: String, ex: Exception): CaseResult {
        val turn = turnRepository.findBySession(session.id).singleOrNull()
        // The first sentence is enough for the table; the test report has the whole message.
        val problem = "${ex.javaClass.simpleName}: ${ex.message.orEmpty().substringBefore(". ")}"
        return turn?.let { resultOf(id, OUTCOME_ERROR, it, details, listOf(problem)) }
            ?: CaseResult(id, OUTCOME_ERROR, BigDecimal.ZERO, maxScore = null, blockedBy = emptyList(), details, listOf(problem))
    }

    /** The summary line of [turn]. */
    protected fun resultOf(id: String, outcome: String, turn: AskTurn, details: String, problems: List<String>): CaseResult {
        val scores = turn.guardrailScores
        return CaseResult(
            id = id,
            outcome = outcome,
            costUsd = turn.costUsd,
            maxScore = scores?.values?.maxOrNull(),
            blockedBy = scores?.let { BedrockGuardrailService.blockedBy(properties.guardrail, turn.mode, it) }.orEmpty(),
            details = details,
            problems = problems,
        )
    }

    private companion object {
        /** A name that only the example profile's index contains. */
        const val EXAMPLE_PROFILE_MARK = "Example Person"

        /** The outcome of a case that failed with an unexpected exception. */
        const val OUTCOME_ERROR = "ERROR"
    }
}