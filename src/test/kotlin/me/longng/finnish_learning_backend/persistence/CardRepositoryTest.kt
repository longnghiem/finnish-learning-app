package me.longng.finnish_learning_backend.persistence

import me.longng.finnish_learning_backend.TestcontainersConfiguration
import me.longng.finnish_learning_backend.controller.dto.CardQueryParams
import me.longng.finnish_learning_backend.domain.Card
import me.longng.finnish_learning_backend.domain.SearchType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class CardRepositoryTest {
    @Autowired
    private lateinit var topicRepository: TopicRepository
    @Autowired
    private lateinit var cardRepository: CardRepository

    private fun firstTopicId(): Int = topicRepository.findAll().first().id

    private fun insertTestCard(
        topicId: Int = firstTopicId(),
        name: String = "syödä",
        exampleSentence: String = "Minä syön jäätelöä",
        translation: String = "to eat",
        imageFilename: String = "test-image.jpg"
    ): Card {
        return cardRepository.insert(
            topicId = topicId,
            name = name,
            exampleSentence = exampleSentence,
            translation = translation,
            imageFilename = imageFilename,
        )
    }

    @Test
    fun testInsertCard() {
        val card = cardRepository.insert(
            topicId = firstTopicId(),
            name = "juoda",
            exampleSentence = "Minä juon vettä",
            translation = "to drink",
            imageFilename = "juoda.jpg",
        )

        assertNotEquals(0, card.id)
        assertEquals(firstTopicId(), card.topicId)
        assertEquals("juoda", card.name)
        assertEquals("Minä juon vettä", card.exampleSentence)
        assertEquals("to drink", card.translation)
        assertEquals("juoda.jpg", card.imageFilename)
        assertNotNull(card.createdAt)
        assertNotNull(card.updatedAt)
    }


    @Test
    fun testFindById() {
        val inserted = insertTestCard()

        val found = cardRepository.findById(inserted.id)

        assertNotNull(found)
        assertEquals(inserted, found)
    }

    @Test
    fun testUpdateCard() {
        val original = insertTestCard(name = "syödä", translation = "to eat")

        val updated = cardRepository.update(
            id = original.id,
            topicId = null,
            name = "juoda",
            exampleSentence = null,
            translation = null,
            imageFilename = null,
        )

        assertNotNull(updated)
        assertEquals("juoda", updated.name)
        assertEquals(original.exampleSentence, updated.exampleSentence)
        assertEquals(original.translation, updated.translation)
        assertEquals(original.imageFilename, updated.imageFilename)
        assertEquals(original.topicId, updated.topicId)
        assertTrue(updated.updatedAt >= original.updatedAt)
    }

    @Test
    fun testDeleteCard() {
        val card = insertTestCard()

        val deleted = cardRepository.deleteById(card.id)

        assertTrue(deleted)
        assertNull(cardRepository.findById(card.id))
    }

    @Test
    fun testQueryCardsByTopic() {
        val topicA = firstTopicId()
        val topicB = topicRepository.findAll()[1].id
        insertTestCard(topicId = topicA, name = "syödä")
        insertTestCard(topicId = topicA, name = "juoda")
        insertTestCard(topicId = topicB, name = "tavata")

        val results = cardRepository.findAll(CardQueryParams(
            topicId = topicA,
            searchType = null,
            searchTerm = null)
        )

        assertEquals(2, results.size)
        assertTrue(results.map { it.name }.containsAll(listOf("syödä", "juoda")))
    }

    @Test
    fun testQueryCardsByVerbName() {
        insertTestCard(name = "Syödä")
        insertTestCard(name = "juoda")
        insertTestCard(name = "tavata")

        val results = cardRepository.findAll(
            CardQueryParams(topicId = null, searchType = SearchType.VERB, searchTerm = "syö"),
        )

        assertEquals(1, results.size)
        assertEquals("Syödä", results.single().name)
    }

    @Test
    fun testQueryCardsByExampleSentence() {
        insertTestCard(name = "syödä", exampleSentence = "Minä syön jäätelöä")
        insertTestCard(name = "juoda", exampleSentence = "Hän juo maitoa")
        insertTestCard(name = "pitää", exampleSentence = "Hän pitää jäätelöstä")

        val results = cardRepository.findAll(
            CardQueryParams(topicId = null, searchType = SearchType.SENTENCE, searchTerm = "jää"),
        )

        assertEquals(2, results.size)
        assertTrue(results.map { it.name }.containsAll(listOf("syödä", "pitää")))

    }
}