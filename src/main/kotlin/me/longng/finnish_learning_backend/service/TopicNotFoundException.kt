package me.longng.finnish_learning_backend.service

/**
 * Thrown when a topic lookup by ID yields no result.
 */
class TopicNotFoundException(val id: Int) :
    RuntimeException("Topic not found with id: $id")