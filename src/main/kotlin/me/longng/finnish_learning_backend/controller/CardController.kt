package me.longng.finnish_learning_backend.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import me.longng.finnish_learning_backend.controller.dto.CardQueryParams
import me.longng.finnish_learning_backend.controller.dto.CardResponse
import me.longng.finnish_learning_backend.domain.SearchType
import me.longng.finnish_learning_backend.service.CardService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/cards")
@Tag(name = "Cards", description = "CRUD and search operations for Finnish vocabulary cards")
class CardController(private val cardService: CardService) {

    /**
     * Creates a new card.
     */
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Create a new card", description = "Image file is required")
    @ApiResponse(responseCode = "201", description = "Card created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid input or missing image")
    @ApiResponse(responseCode = "404", description = "Topic not found")
    fun createCard(
        @RequestParam("name") name: String,
        @RequestParam("exampleSentence") exampleSentence: String,
        @RequestParam("translation") translation: String,
        @RequestParam("topicId") topicId: Int,
        @RequestPart("image") image: MultipartFile,
    ): ResponseEntity<CardResponse> {
        val card = cardService.createCard(
            topicId = topicId,
            name = name,
            exampleSentence = exampleSentence,
            translation = translation,
            image = image,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(CardResponse.from(card))
    }

    /**
     * Updates an existing card. All parameters are optional — only non-null values are applied.
     * If a new image is provided, the old image file is replaced.
     */
    @PutMapping("/{id}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Update an existing card", description = "All fields are optional; only provided values are updated")
    @ApiResponse(responseCode = "200", description = "Card updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid input")
    @ApiResponse(responseCode = "404", description = "Card or topic not found")
    fun updateCard(
        @PathVariable id: Int,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) exampleSentence: String?,
        @RequestParam(required = false) translation: String?,
        @RequestParam(required = false) topicId: Int?,
        @RequestPart("image", required = false) image: MultipartFile?,
    ): ResponseEntity<CardResponse> {
        val card = cardService.updateCard(
            id = id,
            topicId = topicId,
            name = name,
            exampleSentence = exampleSentence,
            translation = translation,
            image = image,
        )
        return ResponseEntity.ok(CardResponse.from(card))
    }

    /**
     * Deletes a card and its associated image.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a card", description = "Deletes the card and its associated image file")
    @ApiResponse(responseCode = "204", description = "Card deleted successfully")
    @ApiResponse(responseCode = "404", description = "Card not found")
    fun deleteCard(@PathVariable id: Int): ResponseEntity<Void> {
        cardService.deleteCard(id)
        return ResponseEntity.noContent().build()
    }

    /**
     * Retrieves a single card by its ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get a card by ID")
    @ApiResponse(responseCode = "200", description = "Card found")
    @ApiResponse(responseCode = "404", description = "Card not found")
    fun getCardById(@PathVariable id: Int): CardResponse =
        CardResponse.from(cardService.getCardById(id))

    // TODO: Support pagination
    /**
     * Queries cards with optional filters. All filters are combined with AND.
     * `searchType` and `searchTerm` must be provided together.
     */
    @GetMapping
    @Operation(
        summary = "Query cards with optional filters",
        description = "All filters are optional and combined with AND. " +
            "searchType and searchTerm must be provided together. " +
            "Search is case-insensitive and uses a substring match.",
    )
    @ApiResponse(responseCode = "200", description = "Cards retrieved successfully")
    @Parameter(name = "searchType", description = "Field to search in: VERB (card name) or SENTENCE (example sentence)")
    @Parameter(name = "searchTerm", description = "Case-insensitive substring to search for")
    fun queryCards(
        @RequestParam(required = false) topicId: Int?,
        @RequestParam(required = false) searchType: SearchType?,
        @RequestParam(required = false) searchTerm: String?,
    ): List<CardResponse> =
        cardService.queryCards(CardQueryParams(topicId, searchType, searchTerm))
            .map { CardResponse.from(it) }
}
