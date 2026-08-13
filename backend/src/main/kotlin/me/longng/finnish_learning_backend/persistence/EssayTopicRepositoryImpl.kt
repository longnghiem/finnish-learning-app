package me.longng.finnish_learning_backend.persistence

import me.longng.finnish_learning_backend.domain.EssayTopic
import me.longng.finnish_learning_backend.persistence.generated.tables.records.EssayTopicsRecord
import me.longng.finnish_learning_backend.persistence.generated.tables.references.ESSAY_TOPICS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class EssayTopicRepositoryImpl(
    private val dsl: DSLContext
): EssayTopicRepository {

    override fun findByTopicId(topicId: Int): List<EssayTopic> {
        return dsl.selectFrom(ESSAY_TOPICS)
            .where(ESSAY_TOPICS.TOPIC_ID.eq(topicId))
            .orderBy(ESSAY_TOPICS.ID.asc())
            .fetch()
            .map { it.toDomain()}
    }

    override fun findById(id: Int): EssayTopic? {
        return dsl.selectFrom(ESSAY_TOPICS)
            .where(ESSAY_TOPICS.ID.eq(id))
            .fetchOne()
            ?.toDomain()
    }

    private fun EssayTopicsRecord.toDomain() = EssayTopic (
        id = requireNotNull(id),
        topicId = requireNotNull(topicId),
        title = requireNotNull(title),
    )
}