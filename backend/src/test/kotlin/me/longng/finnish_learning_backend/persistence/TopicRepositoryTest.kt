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
class TopicRepositoryTest {
    @Autowired
    private lateinit var topicRepository: TopicRepository

    @Test
    fun testFindAll() {
        val topics = topicRepository.findAll()

        assertEquals(7, topics.size)

        val names = topics.map { it.name }
        assertTrue(names.contains("Arkielämä"))
        assertTrue(names.contains("Yhteiskunta"))
        assertTrue(names.contains("Terveys ja hyvinvointi"))
        assertTrue(names.contains("Ihminen ja lähipiiri"))
        assertTrue(names.contains("Luonto ja ympäristö"))
        assertTrue(names.contains("Työ ja koulutus"))
        assertTrue(names.contains("Vapaa-aika ja harrastukset"))
    }

    @Test
    fun testFindById() {
        val allTopics = topicRepository.findAll()
        val expected = allTopics.first()

        val found = topicRepository.findById(expected.id)

        assertNotNull(found)
        assertEquals(expected.id, found.id)
        assertEquals(expected.name, found.name)
    }

    @Test
    fun testFindById_NotFound() {
        val result = topicRepository.findById(99999)

        assertNull(result)
    }

}