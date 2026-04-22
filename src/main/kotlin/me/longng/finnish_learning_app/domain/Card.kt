package me.longng.finnish_learning_app.domain

/**
 * A vocabulary card associating a Finnish verb with its English translation,
 * an example sentence, and an image.
 *
 */
data class Card(
    val id: Int,
    val topicId: Int,
    val name: String,
    val exampleSentence: String,
    val translation: String,
    val imageFilename: String,
    val createdAt: java.time.Instant,
    val updatedAt: java.time.Instant,
)