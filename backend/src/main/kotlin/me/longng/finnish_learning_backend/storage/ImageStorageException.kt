package me.longng.finnish_learning_backend.storage

/**
 * Thrown when an image storage operation fails (e.g., disk write error, file not found).
 */
class ImageStorageException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)