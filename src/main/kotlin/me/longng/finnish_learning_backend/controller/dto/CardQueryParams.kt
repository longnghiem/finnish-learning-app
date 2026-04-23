package me.longng.finnish_learning_backend.controller.dto

import me.longng.finnish_learning_backend.domain.SearchType

/**
 * Optional query parameters for filtering the card list.
 */
data class CardQueryParams(
    val topicId: Int?,
    val searchType: SearchType?,
    val searchTerm: String?,
)