package me.longng.finnish_learning_backend.controller.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Essay evaluation result returned to the client.
 *
 *
 * @property cefrLevel  CEFR sub-level the model assigns to the essay.
 * @property onTopic    `false` when the essay does not address the chosen prompt
 *                      **or** is not written in Finnish.
 * @property issues     Concrete grammar/typo findings. May be empty.
 * @property feedback   Optional prose feedback for the learner — structure, flow, register,
 *                      the things no single [EssayIssue] can express. `null` when the model
 *                      has nothing to add beyond [issues].
 */
data class EvaluateEssayResponse(
    val cefrLevel: EssayCefrLevel,
    val onTopic: Boolean,
    val issues: List<EssayIssue>,
    val feedback: String?,
)

/**
 * CEFR sub-levels an essay can be graded at.
 *
 * JSON values carry a dot (e.g. `"A1.1"`), which is illegal in Kotlin identifiers, so the
 * constants use underscores and map via [JsonProperty].
 *
 */
enum class EssayCefrLevel {
    @JsonProperty("A1.1") A1_1,
    @JsonProperty("A1.2") A1_2,
    @JsonProperty("A2.1") A2_1,
    @JsonProperty("A2.2") A2_2,
    @JsonProperty("B1.1") B1_1,
    @JsonProperty("B1.2") B1_2,
}

/**
 * One concrete problem the model found in the essay.
 *
 * @property kind        Whether this is a grammar mistake or a typo.
 * @property original    The fragment of the learner's essay the issue refers to
 * @property suggestion  The corrected form of [original].
 */
data class EssayIssue(
    val kind: EssayIssueKind,
    val original: String,
    val suggestion: String,
)

/**
 * Category of an [EssayIssue].
 */
enum class EssayIssueKind {
    GRAMMAR,
    TYPO,
}