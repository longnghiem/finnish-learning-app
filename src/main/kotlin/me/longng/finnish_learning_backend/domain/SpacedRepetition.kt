package me.longng.finnish_learning_backend.domain

import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Output of the SM-2 spaced repetition algorithm.
 *
 * Contains the updated schedule state after processing a single review.
 * This is a pure value object — it has no knowledge of the database or
 * the [ReviewSchedule] entity. The calling service is responsible for
 * persisting these values.
 *
 * @property repetition Number of consecutive correct reviews (resets to 0 on failure).
 * @property easeFactor Multiplier controlling interval growth (≥ 1.3). Starts at 2.5.
 * @property intervalDays Days until the next review.
 * @property nextReviewDate The calendar date of the next scheduled review.
 */
data class ReviewUpdate(
    val repetition: Int,
    val easeFactor: Double,
    val intervalDays: Int,
    val nextReviewDate: LocalDate,
)


/**
 * Computes the next review schedule using the **SM-2 (SuperMemo 2)** algorithm.
 *
 * SM-2 was created by Piotr Woźniak in 1987 and is the foundation behind
 * spaced repetition tools like Anki. It adjusts review intervals based on
 * how well the user recalls a card, using a quality grade from 0 (complete
 * blackout) to 5 (perfect recall).
 *
 * ## Algorithm overview
 *
 * 1. **Correct answer (quality ≥ 3):** The repetition count advances and the
 *    interval grows — first to 1 day, then 6 days, then multiplicatively by
 *    the ease factor.
 * 2. **Incorrect answer (quality < 3):** The repetition count resets to 0 and
 *    the interval resets to 1 day — the card is "relearned" from scratch.
 * 3. **Ease factor update (always):** The EF is adjusted based on quality.
 *    Easy cards (q=5) increase EF, making future intervals grow faster.
 *    Hard/incorrect cards decrease EF, slowing future growth. EF is floored
 *    at 1.3 to prevent pathologically short intervals.
 *
 * ## Quality-to-button mapping (used by this app)
 *
 * | Button | Quality | Meaning                         |
 * |--------|---------|---------------------------------|
 * | Again  |    1    | Incorrect, reset schedule       |
 * | Hard   |    3    | Correct with serious difficulty  |
 * | Good   |    4    | Correct with some hesitation     |
 * | Easy   |    5    | Perfect response                 |
 *
 * ## Important ordering detail
 *
 * The interval is calculated using the **current** ease factor (before the
 * update). The EF adjustment is applied afterward. This means a hard answer
 * (q=3) still benefits from the old, higher EF for the current interval —
 * the penalty only affects future reviews.
 *
 * @param quality Self-assessed recall quality (0–5). In this app's UI, only
 *   1, 3, 4, 5 are used, but the algorithm accepts the full 0–5 range.
 * @param currentRepetition Number of consecutive correct reviews so far.
 * @param currentEaseFactor Current ease factor (≥ 1.3).
 * @param currentIntervalDays Current interval in days.
 * @param today The reference date for calculating [ReviewUpdate.nextReviewDate].
 *   Defaults to [LocalDate.now]. Pass a fixed date in tests for determinism.
 * @return The updated schedule state.
 * @throws IllegalArgumentException if [quality] is not in 0..5.
 *
 * @see <a href="https://www.supermemo.com/en/blog/application-of-a-computer-to-improve-the-results-obtained-in-working-with-the-supermemo-method">
 *   SuperMemo SM-2 Algorithm description</a>
 */
fun calculateNextReview(
    quality: Int,
    currentRepetition: Int,
    currentEaseFactor: Double,
    currentIntervalDays: Int,
    today: LocalDate = LocalDate.now(),
): ReviewUpdate {
    require(quality in 0..5) { "Quality must be between 0 and 5, got $quality" }

    // ── Step 1: Calculate new repetition count and interval ──────────────
    //
    // Correct answers (q ≥ 3) advance the schedule:
    //   n=0 → interval=1 (review tomorrow, first successful recall)
    //   n=1 → interval=6 (fixed 6-day gap after second recall)
    //   n≥2 → interval=round(interval × EF) (exponential growth)
    //
    // Incorrect answers (q < 3) reset everything — the card is treated as
    // if the user has never successfully recalled it.
    val newRepetition: Int
    val newIntervalDays: Int

    if (quality >= 3) {
        newIntervalDays = when (currentRepetition) {
            0 -> 1
            1 -> 6
            else -> (currentIntervalDays * currentEaseFactor).roundToInt()
        }
        newRepetition = currentRepetition + 1
    } else {
        newRepetition = 0
        newIntervalDays = 1
    }

    // ── Step 2: Update ease factor ──────────────────────────────────────
    //
    // The formula adjusts EF based on how difficult the recall was:
    //
    //   adjustment = 0.1 - (5 - q) × (0.08 + (5 - q) × 0.02)
    //
    // This produces:  q=5 → +0.10,  q=4 → 0.00,  q=3 → -0.14,
    //                 q=2 → -0.32,  q=1 → -0.54,  q=0 → -0.80
    //
    // The floor of 1.3 prevents the multiplier from becoming so small that
    // intervals barely grow, which would trap the user in an unproductive
    // review loop.
    val qualityDelta = 5 - quality
    val efAdjustment = 0.1 - qualityDelta * (0.08 + qualityDelta * 0.02)
    val newEaseFactor = max(1.3, currentEaseFactor + efAdjustment)

    // ── Step 3: Compute next review date ────────────────────────────────
    val nextReviewDate = today.plusDays(newIntervalDays.toLong())

    return ReviewUpdate(
        repetition = newRepetition,
        easeFactor = newEaseFactor,
        intervalDays = newIntervalDays,
        nextReviewDate = nextReviewDate,
    )
}