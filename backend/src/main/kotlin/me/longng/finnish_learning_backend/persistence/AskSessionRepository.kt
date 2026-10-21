package me.longng.finnish_learning_backend.persistence

import me.longng.finnish_learning_backend.domain.ask.AskSession
import java.time.Instant
import java.util.UUID

/** Stores [AskSession]s of the recruiter chatbot. */
interface AskSessionRepository {

    /**
     * Stores a new session; [AskSession.createdAt] is set by the database.
     *
     * @throws IllegalArgumentException if [ipHash] isn't [AskSession.IP_HASH_LENGTH] characters long
     */
    fun create(id: UUID, ipHash: String, expiresAt: Instant): AskSession

    /** Returns the session, or null if it doesn't exist (never created, or already purged). */
    fun findById(id: UUID): AskSession?

    /** Counts the sessions created for this IP hash at or after [since] (rate limit). */
    fun countCreatedSince(ipHash: String, since: Instant): Int

    /** Deletes one session and, by cascade, its turns. Returns false if it didn't exist. */
    fun deleteById(id: UUID): Boolean

    /**
     * Deletes every session created before [cutoff] and, by cascade, their turns (30-day
     * retention). Returns the number of sessions deleted.
     */
    fun deleteCreatedBefore(cutoff: Instant): Int
}