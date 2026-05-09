package me.longng.finnish_learning_backend.controller.dto

/**
 * Optional query parameters for filtering the card list.
 */
data class CardQueryParams(
    val topicId: Int?,
    val searchType: SearchType?,
    val searchTerm: String?,
)

enum class SearchType {
    VERB,
    SENTENCE,
}