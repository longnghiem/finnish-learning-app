package me.longng.finnish_learning_backend.persistence

import me.longng.finnish_learning_backend.domain.ask.AskSession
import me.longng.finnish_learning_backend.persistence.generated.tables.records.AskSessionsRecord
import me.longng.finnish_learning_backend.persistence.generated.tables.references.ASK_SESSIONS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Repository
class AskSessionRepositoryImpl(
    private val dsl: DSLContext,
): AskSessionRepository {
    override fun create(
        id: UUID,
        ipHash: String,
        expiresAt: Instant
    ): AskSession {
        require(ipHash.length == AskSession.IP_HASH_LENGTH) {
            "ipHash must be ${AskSession.IP_HASH_LENGTH} characters long, got ${ipHash.length}"
        }
        return dsl.insertInto(ASK_SESSIONS)
            .set(ASK_SESSIONS.ID, id)
            .set(ASK_SESSIONS.IP_HASH, ipHash)
            .set(ASK_SESSIONS.EXPIRES_AT, expiresAt.atOffset(ZoneOffset.UTC))
            .returning()
            .fetchSingle()
            .toDomain()
    }

    override fun findById(id: UUID): AskSession? {
        return dsl.selectFrom(ASK_SESSIONS)
            .where(ASK_SESSIONS.ID.eq(id))
            .fetchOne()
            ?.toDomain()
    }

    override fun countCreatedSince(ipHash: String, since: Instant): Int {
        return dsl.fetchCount(
            ASK_SESSIONS,
            ASK_SESSIONS.IP_HASH.eq(ipHash)
                .and(ASK_SESSIONS.CREATED_AT.ge(since.atOffset(ZoneOffset.UTC))),
        )
    }

    override fun deleteById(id: UUID): Boolean {
        return dsl.deleteFrom(ASK_SESSIONS)
            .where(ASK_SESSIONS.ID.eq(id))
            .execute() > 0
    }

    override fun deleteCreatedBefore(cutoff: Instant): Int {
        return dsl.deleteFrom(ASK_SESSIONS)
            .where(ASK_SESSIONS.CREATED_AT.lt(cutoff.atOffset(ZoneOffset.UTC)))
            .execute()
    }

    private fun AskSessionsRecord.toDomain() = AskSession(
        id = requireNotNull(id),
        ipHash = requireNotNull(ipHash),
        createdAt = requireNotNull(createdAt).toInstant(),
        expiresAt = requireNotNull(expiresAt).toInstant(),
    )
}