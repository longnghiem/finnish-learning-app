package me.longng.finnish_learning_app.service

import aQute.bnd.annotation.headers.Category
import me.longng.finnish_learning_app.controller.dto.CardQueryParams
import me.longng.finnish_learning_app.domain.Card
import me.longng.finnish_learning_app.persistence.CardRepository
import me.longng.finnish_learning_app.persistence.TopicRepository
import me.longng.finnish_learning_app.storage.ImageStorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

@Service
class CardService(
    private val cardRepository: CardRepository,
    private val topicRepository: TopicRepository,
    private val imageStorageService: ImageStorageService,
) {
    private val logger = LoggerFactory.getLogger(CardService::class.java)

    @Transactional
    fun createCard(
        topicId: Int,
        name: String,
        exampleSentence: String,
        translation: String,
        image: MultipartFile,
    ): Card {
        require(name.isNotBlank()) { "Card name must not be blank" }
        require(exampleSentence.isNotBlank()) { "Card example must not be blank" }
        require(translation.isNotBlank()) { "Card translation must not be blank" }
        topicRepository.findById(topicId) ?: throw TopicNotFoundException(topicId)

        val imageFileName = imageStorageService.store(image)

        val card = cardRepository.insert(
            topicId = topicId,
            name = name,
            exampleSentence = exampleSentence,
            translation = translation,
            imageFilename = imageFileName,
        )

        logger.info("Created new card: $card")
        return card
    }

    /**
     * Updates an existing card. Only non-null parameters are applied.
     * If a new [image] is provided, the old image file is deleted from storage and replaced with the new one.
     */
    @Transactional
    fun updateCard(
        id: Int,
        topicId: Int?,
        name: String?,
        exampleSentence: String?,
        translation: String?,
        image: MultipartFile?,
    ): Card {
        val existingCard = cardRepository.findById(id) ?: throw CardNotFoundException(id)

        if (topicId != null && existingCard.topicId != topicId) {
            topicRepository.findById(topicId) ?: throw TopicNotFoundException(topicId)
        }

        val newImageFilename = image?.let { newImage ->
            val newName = imageStorageService.store(newImage)
            imageStorageService.delete(existingCard.imageFilename)
            newName
        }

        val updatedCard = cardRepository.update(
            id = id,
            topicId = topicId,
            name = name,
            exampleSentence = exampleSentence,
            translation = translation,
            imageFilename = newImageFilename,
        )

        check(updatedCard != null) { "Card update failed for card with id $id" }

        logger.info("Updated card with id={}", id)
        return updatedCard
    }

    /**
     * Deletes a card and its associated image file.
     */
    @Transactional
    fun deleteCard(id: Int) {
        val card = cardRepository.findById(id) ?: throw CardNotFoundException(id)

        cardRepository.deleteById(id)
        imageStorageService.delete(card.imageFilename)

        logger.info("Deleted card with id={}", id)
    }

    /**
     * Fetches a single card by ID.
     */
    @Transactional(readOnly = true)
    fun getCardById(id: Int): Card =
        cardRepository.findById(id) ?: throw CardNotFoundException(id)

    /**
     * Queries cards with optional filters (topic, search type, search term).
     * All filters are ANDed together. Returns cards ordered by creation date descending.
     */
    @Transactional(readOnly = true)
    fun queryCards(query: CardQueryParams): List<Card> =
        cardRepository.findAll(query)
}