package me.longng.finnish_learning_backend.persistence

import me.longng.finnish_learning_backend.domain.UserTopicStats
import me.longng.finnish_learning_backend.persistence.generated.tables.records.UserTopicStatsRecord
import me.longng.finnish_learning_backend.persistence.generated.tables.references.USER_TOPIC_STATS
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

/**
 * This is the Kafka consumer's write target. The [upsert] method is designed
 * to be called once per quiz answer event and atomically updates all counters
 * and streaks using PostgreSQL's `INSERT … ON CONFLICT … DO UPDATE` with
 * SQL expressions.
 */
@Repository
class UserTopicStatsRepositoryImpl(
    private val dsl: DSLContext,
) : UserTopicStatsRepository {
    override fun upsert(userId: Int, topicId: Int, correct: Boolean) {
        val now = OffsetDateTime.now()
        val correctIncrement = if (correct) 1 else 0
        val initialStreak = if (correct) 1 else 0

        // INSERT branch: first ever answer for this user–topic pair.
        // DO UPDATE branch: increment counters from the existing row.
        //
        // Streak logic:
        //   correct answer  → current_streak + 1
        //   incorrect answer → reset to 0
        //   best_streak      → GREATEST(best_streak, new current_streak)
        //
        // Using SQL expressions (not Kotlin values) in the DO UPDATE ensures
        // the update is atomic even under concurrent Kafka consumer processing.
        dsl.insertInto(USER_TOPIC_STATS)
            .set(USER_TOPIC_STATS.USER_ID, userId)
            .set(USER_TOPIC_STATS.TOPIC_ID, topicId)
            .set(USER_TOPIC_STATS.TOTAL_REVIEWS, 1)
            .set(USER_TOPIC_STATS.CORRECT_REVIEWS, correctIncrement)
            .set(USER_TOPIC_STATS.CURRENT_STREAK, initialStreak)
            .set(USER_TOPIC_STATS.BEST_STREAK, initialStreak)
            .set(USER_TOPIC_STATS.LAST_REVIEWED_AT, now)
            .set(USER_TOPIC_STATS.UPDATED_AT, now)
            .onConflict(USER_TOPIC_STATS.USER_ID, USER_TOPIC_STATS.TOPIC_ID)
            .doUpdate()
            .set(USER_TOPIC_STATS.TOTAL_REVIEWS, USER_TOPIC_STATS.TOTAL_REVIEWS.plus(1))
            .set(USER_TOPIC_STATS.CORRECT_REVIEWS, USER_TOPIC_STATS.CORRECT_REVIEWS.plus(correctIncrement))
            .set(
                USER_TOPIC_STATS.CURRENT_STREAK,
                if (correct) USER_TOPIC_STATS.CURRENT_STREAK.plus(1) else DSL.inline(0)
            )
            .set(
                USER_TOPIC_STATS.BEST_STREAK,
                DSL.greatest(
                    USER_TOPIC_STATS.BEST_STREAK,
                    if (correct) USER_TOPIC_STATS.CURRENT_STREAK.plus(1) else DSL.inline(0)
                )
            )
            .set(USER_TOPIC_STATS.LAST_REVIEWED_AT, now)
            .set(USER_TOPIC_STATS.UPDATED_AT, now)
            .execute()
    }

    override fun findByUser(userId: Int): List<UserTopicStats> {
        return dsl.selectFrom(USER_TOPIC_STATS)
            .where(USER_TOPIC_STATS.USER_ID.eq(userId))
            .fetch()
            .map { it.toDomain() }
    }

    override fun findByUserAndTopic(userId: Int, topicId: Int): UserTopicStats? {
        return dsl.selectFrom(USER_TOPIC_STATS)
            .where(
                USER_TOPIC_STATS.USER_ID.eq(userId)
                    .and(USER_TOPIC_STATS.TOPIC_ID.eq(topicId)),
            )
            .fetchOne()
            ?.toDomain()
    }

    private fun UserTopicStatsRecord.toDomain() = UserTopicStats(
        id = id!!,
        userId = userId!!,
        topicId = topicId!!,
        totalReviews = totalReviews!!,
        correctReviews = correctReviews!!,
        currentStreak = currentStreak!!,
        bestStreak = bestStreak!!,
        lastReviewedAt = lastReviewedAt?.toInstant(),
        updatedAt = updatedAt!!.toInstant(),
    )
}