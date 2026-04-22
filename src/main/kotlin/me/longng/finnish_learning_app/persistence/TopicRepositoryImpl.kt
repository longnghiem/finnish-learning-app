package me.longng.finnish_learning_app.persistence

import me.longng.finnish_learning_app.domain.Topic
import me.longng.finnish_learning_app.persistence.generated.tables.records.TopicsRecord
import me.longng.finnish_learning_app.persistence.generated.tables.references.TOPICS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class TopicRepositoryImpl(
    private val dsl: DSLContext
): TopicRepository {
    override fun findAll(): List<Topic> {
        return dsl.selectFrom(TOPICS)
            .orderBy(TOPICS.NAME.asc())
            .fetch()
            .map { it.toDomain() }
    }

    override fun findById(id: Int): Topic? {
        return dsl.selectFrom(TOPICS)
            .where(TOPICS.ID.eq(id))
            .fetchOne()
            ?.toDomain()
    }

    private fun TopicsRecord.toDomain() = Topic(
        id = id!!,
        name = name!!,
    )
}