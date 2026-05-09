package me.longng.finnish_learning_backend.persistence

import me.longng.finnish_learning_backend.controller.dto.CardQueryParams
import me.longng.finnish_learning_backend.domain.Card

interface CardRepository {

    /**
     * Persists a new card and returns the persisted entity (with generated ID and timestamps).
     */
    fun insert(
        topicId: Int,
        name: String,
        exampleSentence: String,
        translation: String,
        imageFilename: String,
    ): Card

    /**
     * Updates an existing card. Only non-null parameters are applied.
     * Always updates [Card.updatedAt] to the current timestamp.
     *
     * @return The updated card, or null if no card with [id] exists.
     */
    fun update(
        id: Int,
        topicId: Int?,
        name: String?,
        exampleSentence: String?,
        translation: String?,
        imageFilename: String?,
    ): Card?

    /**
     * Deletes a card by its primary key.
     *
     * @return true if a row was deleted, false if no card with [id] existed.
     */
    fun deleteById(id: Int): Boolean

    /**
     * Fetches a single card by primary key.
     *
     * @return The card, or null if not found.
     */
    fun findById(id: Int): Card?

    /**
     * Queries cards with optional filters. All filters are ANDed together.
     * Search is case-insensitive and uses a substring (contains) match.
     *
     * @param query The filter parameters; all fields are optional.
     * @return Matching cards ordered by [Card.createdAt] descending.
     */
    fun findAll(query: CardQueryParams): List<Card>

    /**
     * Finds cards in a topic that the user has never reviewed.
     *
     * A card is "new" if no row exists in `review_schedules` for the given
     * user–card pair.
     *
     * @param userId The user to check review history for.
     * @param topicId The topic to draw cards from.
     * @param limit Maximum number of new cards to return.
     * @return Cards ordered by [Card.createdAt] ASC (oldest first).
     */
    fun findNewCardsForUser(userId: Int, topicId: Int, limit: Int): List<Card>

    /**
     * Counts how many cards belong to a topic.
     * @return The number of cards in the topic. Returns 0 if the topic has no cards (or does not exist).
     */
    fun countByTopicId(topicId: Int): Int
}