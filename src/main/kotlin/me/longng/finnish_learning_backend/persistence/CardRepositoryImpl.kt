package me.longng.finnish_learning_backend.persistence

import me.longng.finnish_learning_backend.controller.dto.CardQueryParams
import me.longng.finnish_learning_backend.domain.Card
import me.longng.finnish_learning_backend.domain.SearchType
import me.longng.finnish_learning_backend.persistence.generated.tables.records.CardsRecord
import me.longng.finnish_learning_backend.persistence.generated.tables.references.CARDS
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class CardRepositoryImpl(
    private val dsl: DSLContext,
): CardRepository {
    override fun insert(
        topicId: Int,
        name: String,
        exampleSentence: String,
        translation: String,
        imageFilename: String
    ): Card {
        val record = dsl.insertInto(CARDS)
            .set(CARDS.TOPIC_ID, topicId)
            .set(CARDS.NAME, name)
            .set(CARDS.EXAMPLE_SENTENCE, exampleSentence)
            .set(CARDS.TRANSLATION, translation)
            .set(CARDS.IMAGE_FILENAME, imageFilename)
            .returning()
            .fetchOne()

        check(record != null) { "INSERT into cards returned no record" }
        return record.toDomain()
    }

    override fun update(
        id: Int,
        topicId: Int?,
        name: String?,
        exampleSentence: String?,
        translation: String?,
        imageFilename: String?
    ): Card? {
        var step = dsl.update(CARDS)
            .set(CARDS.UPDATED_AT, OffsetDateTime.now())

        if (topicId != null) {
            step = step.set(CARDS.TOPIC_ID, topicId)
        }
        if (name != null) {
            step = step.set(CARDS.NAME, name)
        }
        if (exampleSentence != null) {
            step = step.set(CARDS.EXAMPLE_SENTENCE, exampleSentence)
        }
        if (translation != null) {
            step = step.set(CARDS.TRANSLATION, translation)
        }
        if (imageFilename != null) {
            step = step.set(CARDS.IMAGE_FILENAME, imageFilename)
        }

        val rowsAffected = step
            .where(CARDS.ID.eq(id))
            .execute()

        if (rowsAffected == 0) return null

        // Re-fetch to return the full updated card with all fields
        return findById(id)
    }

    override fun deleteById(id: Int): Boolean {
        val rowsDeleted = dsl.deleteFrom(CARDS)
            .where(CARDS.ID.eq(id))
            .execute()

        return rowsDeleted > 0
    }

    override fun findById(id: Int): Card? {
        return dsl.selectFrom(CARDS)
            .where(CARDS.ID.eq(id))
            .fetchOne()
            ?.toDomain()
    }

    override fun findAll(query: CardQueryParams): List<Card> {
        var condition = DSL.noCondition()

        if (query.topicId != null) {
            condition = condition.and(CARDS.TOPIC_ID.eq(query.topicId))
        }

        if (query.searchType != null && !query.searchTerm.isNullOrBlank()) {
            val field = when (query.searchType) {
                SearchType.VERB -> CARDS.NAME
                SearchType.SENTENCE -> CARDS.EXAMPLE_SENTENCE
            }
            condition = condition.and(field.containsIgnoreCase(query.searchTerm))
        }

        return dsl.selectFrom(CARDS)
            .where(condition)
            .orderBy(CARDS.CREATED_AT.desc())
            .fetch()
            .map { it.toDomain() }

    }

    private fun CardsRecord.toDomain() = Card(
        id = id!!,
        topicId = topicId!!,
        name = name!!,
        exampleSentence = exampleSentence!!,
        translation = translation!!,
        imageFilename = imageFilename!!,
        createdAt = createdAt!!.toInstant(),
        updatedAt = updatedAt!!.toInstant(),
    )
}