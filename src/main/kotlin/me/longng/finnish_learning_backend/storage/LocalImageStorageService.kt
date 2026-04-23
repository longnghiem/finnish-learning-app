package me.longng.finnish_learning_backend.storage

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

@Service
class LocalImageStorageService(
    @Value("\${app.storage.location}") location: String,
): ImageStorageService {
    private val logger = LoggerFactory.getLogger(LocalImageStorageService::class.java)

    private val storageRoot: Path = Path.of(location).toAbsolutePath()

    @PostConstruct
    fun init() {
        storageRoot.createDirectories()
        logger.info("Image storage initialised at: {}", storageRoot)
    }

    override fun store(file: MultipartFile): String {
        val originalFileName = file.originalFilename
        require(!originalFileName.isNullOrBlank()) { "Uploaded file name must not be null or blank" }

        val extension = originalFileName.substringAfterLast(".", "")
        val uniqueFileName = if (extension.isNotBlank()) {
            "${UUID.randomUUID()}.$extension"
        } else {
            UUID.randomUUID().toString()
        }

        val targetPath = storageRoot.resolve(uniqueFileName)

        try {
            targetPath.writeBytes(file.bytes)
        } catch (ex: IOException) {
            logger.error("Failed to store image as {}", uniqueFileName, ex)
            throw ImageStorageException("Failed to store image: $uniqueFileName", ex)
        }

        logger.info("Stored image: {}", uniqueFileName)
        return uniqueFileName
    }

    override fun load(filename: String): ByteArray {
        require(filename.isNotBlank()) { "File name must not be null or blank" }

        val path = storageRoot.resolve(filename)

        if (!path.exists()) {
            throw ImageStorageException("Image not found: $filename")
        }

        return try {
            path.readBytes()
        } catch (ex: IOException) {
            logger.error("Failed to read image: {}", filename, ex)
            throw ImageStorageException("Failed to read image: $filename", ex)
        }
    }

    override fun delete(filename: String) {
        require(filename.isNotBlank()) { "Filename must not be blank" }

        val path = storageRoot.resolve(filename)

        if (!path.exists()) {
            logger.warn("Image not found for deletion (no-op): {}", filename)
            return
        }

        try {
            path.deleteExisting()
        } catch (ex: IOException) {
            logger.error("Failed to delete image: {}", filename, ex)
            throw ImageStorageException("Failed to delete image: $filename", ex)
        }

        logger.info("Deleted image: {}", filename)
    }
}