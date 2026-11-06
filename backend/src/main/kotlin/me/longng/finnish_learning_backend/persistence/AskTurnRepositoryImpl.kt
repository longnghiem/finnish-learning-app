package me.longng.finnish_learning_backend.persistence

import me.longng.finnish_learning_backend.domain.ask.AskMode
import me.longng.finnish_learning_backend.domain.ask.AskTurn
import me.longng.finnish_learning_backend.domain.ask.GuardrailOutcome
import me.longng.finnish_learning_backend.domain.ask.TokenUsage
import me.longng.finnish_learning_backend.domain.ask.ToolCall
import me.longng.finnish_learning_backend.domain.ask.TurnCompletion
import me.longng.finnish_learning_backend.domain.ask.TurnFilter
import me.longng.finnish_learning_backend.domain.ask.TurnStatus
import me.longng.finnish_learning_backend.persistence.generated.tables.records.AskTurnsRecord
import me.longng.finnish_learning_backend.persistence.generated.tables.references.ASK_SESSIONS
import me.longng.finnish_learning_backend.persistence.generated.tables.references.ASK_TURNS
import org.jooq.CommonTableExpression
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.Record1
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Repository
class AskTurnRepositoryImpl(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
) : AskTurnRepository {

    override fun insertPending(sessionId: UUID, mode: AskMode, userText: String): Long {
        return requireNotNull(
            dsl.insertInto(ASK_TURNS)
                .set(ASK_TURNS.SESSION_ID, sessionId)
                .set(ASK_TURNS.MODE, mode.name)
                .set(ASK_TURNS.STATUS, TurnStatus.PENDING.name)
                .set(ASK_TURNS.USER_TEXT, userText)
                .returning(ASK_TURNS.ID)
                .fetchSingle()
                .id,
        )
    }

    override fun complete(id: Long, completion: TurnCompletion) {
        val updated = dsl.update(ASK_TURNS)
            .set(ASK_TURNS.STATUS, completion.status.name)
            .set(ASK_TURNS.ANSWER_TEXT, completion.answerText)
            .set(ASK_TURNS.FIT_REPORT, completion.fitReportJson?.let { JSONB.valueOf(it) })
            .set(ASK_TURNS.TOOL_TRACE, toJsonb(completion.toolTrace))
            .set(ASK_TURNS.GUARDRAIL_OUTCOME, completion.guardrailOutcome?.name)
            .set(ASK_TURNS.GUARDRAIL_SCORES, completion.guardrailScores?.let { toJsonb(it) })
            .set(ASK_TURNS.INPUT_TOKENS, completion.tokens.inputTokens)
            .set(ASK_TURNS.OUTPUT_TOKENS, completion.tokens.outputTokens)
            .set(ASK_TURNS.CACHE_READ_TOKENS, completion.tokens.cacheReadTokens)
            .set(ASK_TURNS.CACHE_WRITE_TOKENS, completion.tokens.cacheWriteTokens)
            .set(ASK_TURNS.GUARDRAIL_TEXT_UNITS, completion.guardrailTextUnits)
            .set(ASK_TURNS.COST_USD, completion.costUsd)
            .set(ASK_TURNS.LATENCY_MS, completion.latencyMs)
            .where(ASK_TURNS.ID.eq(id))
            .and(ASK_TURNS.STATUS.eq(TurnStatus.PENDING.name))
            .execute()
        check(updated == 1) { "Turn $id doesn't exist or was already completed" }
    }

    override fun findBySession(sessionId: UUID): List<AskTurn> {
        return dsl.selectFrom(ASK_TURNS)
            .where(ASK_TURNS.SESSION_ID.eq(sessionId))
            // Turns created in the same transaction share created_at; the id keeps their order.
            .orderBy(ASK_TURNS.CREATED_AT.asc(), ASK_TURNS.ID.asc())
            .fetch()
            .map { it.toDomain() }
    }

    override fun countTurns(sessionId: UUID, mode: AskMode): Int {
        return dsl.fetchCount(
            ASK_TURNS,
            ASK_TURNS.SESSION_ID.eq(sessionId).and(ASK_TURNS.MODE.eq(mode.name)),
        )
    }

    override fun countForIpSince(ipHash: String, mode: AskMode, since: Instant): Int {
        val sessions = sessionsWithIpHash(ipHash)
        return dsl.with(sessions)
            .selectCount()
            .from(ASK_TURNS)
            .innerJoin(sessions).on(ASK_TURNS.SESSION_ID.eq(sessions.field(SESSION_ID_FIELD, UUID::class.java)))
            .where(ASK_TURNS.MODE.eq(mode.name))
            .and(ASK_TURNS.STATUS.ne(TurnStatus.CAPPED.name))
            .and(ASK_TURNS.CREATED_AT.ge(since.atOffset(ZoneOffset.UTC)))
            .fetchSingle()
            .value1()
    }

    override fun findPage(filter: TurnFilter, limit: Int, offset: Int): List<AskTurn> {
        require(limit in 1..MAX_PAGE_SIZE) { "limit must be in 1..$MAX_PAGE_SIZE" }
        require(offset >= 0) { "offset must not be negative" }

        // Optional join-based filter as a CTE: one row per matching session, created only when
        // the filter is set.
        val sessions = filter.ipHash?.let { sessionsWithIpHash(it) }

        val conditions: List<Condition> = listOfNotNull(
            filter.mode?.let { ASK_TURNS.MODE.eq(it.name) },
            filter.status?.let { ASK_TURNS.STATUS.eq(it.name) },
            filter.from?.let { ASK_TURNS.CREATED_AT.ge(it.atOffset(ZoneOffset.UTC)) },
            filter.to?.let { ASK_TURNS.CREATED_AT.lt(it.atOffset(ZoneOffset.UTC)) },
        )

        return dsl.with(listOfNotNull(sessions))
            .select(ASK_TURNS.asterisk())
            .from(
                if (sessions == null) {
                    ASK_TURNS
                } else {
                    ASK_TURNS.innerJoin(sessions)
                        .on(ASK_TURNS.SESSION_ID.eq(sessions.field(SESSION_ID_FIELD, UUID::class.java)))
                },
            )
            .where(conditions)
            .orderBy(ASK_TURNS.CREATED_AT.desc(), ASK_TURNS.ID.desc())
            .limit(limit)
            .offset(offset)
            .fetchInto(ASK_TURNS)
            .map { it.toDomain() }
    }

    override fun failPending(): Int {
        return dsl.update(ASK_TURNS)
            .set(ASK_TURNS.STATUS, TurnStatus.ERROR.name)
            .where(ASK_TURNS.STATUS.eq(TurnStatus.PENDING.name))
            .execute()
    }

    /** CTE with the id of every session that has this IP hash, each id once. */
    private fun sessionsWithIpHash(ipHash: String): CommonTableExpression<Record1<UUID?>> {
        return DSL.name(SESSIONS_CTE_NAME)
            .fields(SESSION_ID_FIELD)
            .`as`(
                DSL.selectDistinct(ASK_SESSIONS.ID)
                    .from(ASK_SESSIONS)
                    .where(ASK_SESSIONS.IP_HASH.eq(ipHash)),
            )
    }

    private fun toJsonb(value: Any): JSONB = JSONB.valueOf(objectMapper.writeValueAsString(value))

    private fun AskTurnsRecord.toDomain() = AskTurn(
        id = requireNotNull(id),
        sessionId = requireNotNull(sessionId),
        mode = AskMode.valueOf(requireNotNull(mode)),
        status = TurnStatus.valueOf(requireNotNull(status)),
        userText = requireNotNull(userText),
        answerText = answerText,
        fitReportJson = fitReport?.data(),
        toolTrace = objectMapper.readValue<List<ToolCall>>(requireNotNull(toolTrace).data()),
        guardrailOutcome = guardrailOutcome?.let { GuardrailOutcome.valueOf(it) },
        guardrailScores = guardrailScores?.let { objectMapper.readValue<Map<String, Double>>(it.data()) },
        tokens = TokenUsage(
            inputTokens = requireNotNull(inputTokens),
            outputTokens = requireNotNull(outputTokens),
            cacheReadTokens = requireNotNull(cacheReadTokens),
            cacheWriteTokens = requireNotNull(cacheWriteTokens),
        ),
        guardrailTextUnits = requireNotNull(guardrailTextUnits),
        costUsd = requireNotNull(costUsd),
        latencyMs = latencyMs,
        createdAt = requireNotNull(createdAt).toInstant(),
    )

    private companion object {
        /** Largest page [findPage] returns. */
        const val MAX_PAGE_SIZE = 100
        const val SESSIONS_CTE_NAME = "sessions_with_ip"
        const val SESSION_ID_FIELD = "session_id"
    }
}