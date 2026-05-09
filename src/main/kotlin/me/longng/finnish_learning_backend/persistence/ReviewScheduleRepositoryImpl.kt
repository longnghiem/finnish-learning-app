package me.longng.finnish_learning_backend.persistence

import me.longng.finnish_learning_backend.domain.ReviewSchedule
import me.longng.finnish_learning_backend.persistence.generated.tables.records.ReviewSchedulesRecord
import me.longng.finnish_learning_backend.persistence.generated.tables.references.CARDS
import me.longng.finnish_learning_backend.persistence.generated.tables.references.REVIEW_SCHEDULES
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

@Repository
class ReviewScheduleRepositoryImpl(
    private val dsl: DSLContext,
) : ReviewScheduleRepository {
    override fun upsert(
        userId: Int,
        cardId: Int,
        repetition: Int,
        easeFactor: Double,
        intervalDays: Int,
        nextReviewDate: LocalDate
    ): ReviewSchedule {
        // ON CONFLICT on the unique (user_id, card_id) constraint handles both
        // the "first time reviewing this card" (INSERT) and "reviewing again" (UPDATE)
        // cases in a single atomic statement.
        dsl.insertInto(REVIEW_SCHEDULES)
            .set(REVIEW_SCHEDULES.USER_ID, userId)
            .set(REVIEW_SCHEDULES.CARD_ID, cardId)
            .set(REVIEW_SCHEDULES.REPETITION, repetition)
            .set(REVIEW_SCHEDULES.EASE_FACTOR, BigDecimal.valueOf(easeFactor))
            .set(REVIEW_SCHEDULES.INTERVAL_DAYS, intervalDays)
            .set(REVIEW_SCHEDULES.NEXT_REVIEW_DATE, nextReviewDate)
            .onConflict(REVIEW_SCHEDULES.USER_ID, REVIEW_SCHEDULES.CARD_ID)
            .doUpdate()
            .set(REVIEW_SCHEDULES.REPETITION, repetition)
            .set(REVIEW_SCHEDULES.EASE_FACTOR, BigDecimal.valueOf(easeFactor))
            .set(REVIEW_SCHEDULES.INTERVAL_DAYS, intervalDays)
            .set(REVIEW_SCHEDULES.NEXT_REVIEW_DATE, nextReviewDate)
            .set(REVIEW_SCHEDULES.LAST_REVIEWED_AT, OffsetDateTime.now())
            .execute()

        val schedule = findByUserAndCard(userId, cardId)
        check(schedule != null) { "Failed to upsert review schedule for user $userId and card $cardId" }
        return schedule
    }

    override fun findByUserAndCard(userId: Int, cardId: Int): ReviewSchedule? {
        return dsl.selectFrom(REVIEW_SCHEDULES)
            .where(
                REVIEW_SCHEDULES.USER_ID.eq(userId)
                    .and(REVIEW_SCHEDULES.CARD_ID.eq(cardId)),
            )
            .fetchOne()
            ?.toDomain()
    }

    override fun findDueCards(
        userId: Int,
        topicId: Int,
        limit: Int
    ): List<ReviewSchedule> {
        return dsl.select(REVIEW_SCHEDULES.asterisk())
            .from(REVIEW_SCHEDULES)
            .join(CARDS).on(CARDS.ID.eq(REVIEW_SCHEDULES.CARD_ID))
            .where(
                REVIEW_SCHEDULES.USER_ID.eq(userId)
                .and(CARDS.TOPIC_ID.eq(topicId))
                .and(REVIEW_SCHEDULES.NEXT_REVIEW_DATE.le(LocalDate.now())),
            )
            .orderBy(REVIEW_SCHEDULES.NEXT_REVIEW_DATE.asc())
            .limit(limit)
            .fetchInto(REVIEW_SCHEDULES)
            .map { it.toDomain() }
    }

    override fun countByUserAndTopic(userId: Int, topicId: Int): Int {
        return dsl.selectCount()
            .from(REVIEW_SCHEDULES)
            .join(CARDS).on(REVIEW_SCHEDULES.CARD_ID.eq(CARDS.ID))
            .where(
                REVIEW_SCHEDULES.USER_ID.eq(userId)
                    .and(CARDS.TOPIC_ID.eq(topicId)),
            )
            .fetchOne(0, Int::class.java) ?: 0
    }

    override fun countDueByUserAndTopic(userId: Int, topicId: Int): Int {
        return dsl.selectCount()
            .from(REVIEW_SCHEDULES)
            .join(CARDS).on(REVIEW_SCHEDULES.CARD_ID.eq(CARDS.ID))
            .where(
                REVIEW_SCHEDULES.USER_ID.eq(userId)
                    .and(CARDS.TOPIC_ID.eq(topicId))
                    .and(REVIEW_SCHEDULES.NEXT_REVIEW_DATE.le(LocalDate.now())),
            )
            .fetchOne(0, Int::class.java) ?: 0
    }

    private fun ReviewSchedulesRecord.toDomain() = ReviewSchedule (
        id = id!!,
        userId = userId!!,
        cardId = cardId!!,
        repetition = repetition!!,
        easeFactor = easeFactor!!.toDouble(),
        intervalDays = intervalDays!!,
        nextReviewDate = nextReviewDate!!,
        lastReviewedAt = lastReviewedAt?.toInstant()
    )
}