package me.longng.finnish_learning_backend.persistence

import me.longng.finnish_learning_backend.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class EssayTopicRepositoryTest {

    @Autowired
    private lateinit var essayTopicRepository: EssayTopicRepository

    @Autowired
    private lateinit var topicRepository: TopicRepository

    @Test
    fun testFindByTopicId_ReturnsSeededPrompts() {
        val topicId = topicRepository.findAll().firstOrNull {it.name == "Arkielämä" }?.id
        checkNotNull(topicId)

        val prompts = essayTopicRepository.findByTopicId(topicId)

        assertTrue(prompts.size >= 3, "Expected at least 3 seeded prompts, got ${prompts.size}")
        assertTrue(prompts.all { it.topicId == topicId })
        assertTrue(prompts.all { it.title.isNotBlank() })
    }

    @Test
    fun testFindByTopicId_UnknownTopic() {
        val prompts = essayTopicRepository.findByTopicId(99999)

        assertEquals(emptyList(), prompts)
    }

    @Test
    fun testFindByTopicId_IsOrdered() {
        val topicId = topicRepository.findAll().firstOrNull {it.name == "Arkielämä" }?.id
        checkNotNull(topicId)

        val first = essayTopicRepository.findByTopicId(topicId)
        val second = essayTopicRepository.findByTopicId(topicId)

        assertEquals(first.map { it.id }, second.map { it.id })
        assertEquals(first.map { it.id }.sorted(), first.map { it.id })
    }

    @Test
    fun testFindById_Found() {
        val topicId = topicRepository.findAll().firstOrNull {it.name == "Arkielämä" }?.id
        checkNotNull(topicId)

        val expected = essayTopicRepository.findByTopicId(topicId).first()

        val found = essayTopicRepository.findById(expected.id)

        assertNotNull(found)
        assertEquals(expected.id, found.id)
        assertEquals(expected.topicId, found.topicId)
        assertEquals(expected.title, found.title)
    }

    @Test
    fun testFindById_NotFound() {
        val result = essayTopicRepository.findById(99999)

        assertNull(result)
    }

}