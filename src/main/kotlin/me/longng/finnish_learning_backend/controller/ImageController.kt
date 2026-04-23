package me.longng.finnish_learning_backend.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import me.longng.finnish_learning_backend.storage.ImageStorageService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URLConnection

/**
 * Serves card images stored on the server by filename.
 */
@RestController
@RequestMapping("/api/images")
@Tag(name = "Images", description = "Retrieve card images stored on the server")
class ImageController(private val imageStorageService: ImageStorageService) {

    /**
     * Returns the raw image bytes for the given filename.
     * Path traversal characters are rejected as a security measure.
     */
    @GetMapping("/{filename}")
    @Operation(summary = "Get an image by filename", description = "Returns the raw image bytes")
    @ApiResponse(responseCode = "200", description = "Image bytes returned successfully")
    @ApiResponse(responseCode = "404", description = "Image not found")
    fun getImage(@PathVariable filename: String): ResponseEntity<ByteArray> {
        require(!filename.contains("..") && !filename.contains("/")) {
            "Invalid filename: path traversal is not allowed"
        }

        val bytes = imageStorageService.load(filename)
        val contentType = URLConnection.guessContentTypeFromName(filename) ?: "application/octet-stream"

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .body(bytes)
    }
}
