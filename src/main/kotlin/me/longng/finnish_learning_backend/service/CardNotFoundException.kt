package me.longng.finnish_learning_backend.service

/**
 * Thrown when a card lookup by ID yields no result.
 */
class CardNotFoundException(val id: Int) :
    RuntimeException("Card not found with id: $id")