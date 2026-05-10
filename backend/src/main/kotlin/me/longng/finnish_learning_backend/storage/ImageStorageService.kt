package me.longng.finnish_learning_backend.storage

import org.springframework.web.multipart.MultipartFile

/**
 * Abstracts image persistence, allowing the storage backend (local filesystem, S3, GCS, etc.)
 * to be swapped without changing the service or controller layers.
 */
interface ImageStorageService {

    /**
     * Persists the given multipart file to the backing store.
     *
     * @param file The uploaded image file.
     * @return The generated filename under which the image is stored.
     * @throws ImageStorageException if the file cannot be written.
     */
    fun store(file: MultipartFile): String

    /**
     * Loads the raw bytes of a stored image.
     *
     * @param filename The filename returned by [store].
     * @return The image bytes.
     * @throws ImageStorageException if the file does not exist or cannot be read.
     */
    fun load(filename: String): ByteArray

    /**
     * Deletes a stored image. No-op if the file does not exist.
     *
     * @param filename The filename returned by [store].
     * @throws ImageStorageException if the file exists but cannot be deleted.
     */
    fun delete(filename: String)
}