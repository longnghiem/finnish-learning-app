package me.longng.finnish_learning_backend.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * Unit tests for the SM-2 spaced repetition algorithm ([calculateNextReview]).
 *
 * These are **pure unit tests** — no Spring context, no database, no I/O.
 * Each test verifies a specific behaviour of the algorithm with pre-computed
 * expected values derived from the SM-2 formula.
 *
 * A fixed [TODAY] date is used throughout so that [ReviewUpdate.nextReviewDate]
 * assertions are deterministic and not affected by the real system clock.
 */
class SpacedRepetitionTest {

    companion object {
        /** Fixed reference date for deterministic date assertions. */
        private val TODAY = LocalDate.of(2026, 1, 1)

        /** SM-2 default ease factor for a brand-new card. */
        private const val DEFAULT_EF = 2.5

        /**
         * Floating-point comparison tolerance.
         * The EF formula involves multiplication and addition of doubles, so
         * we allow a tiny rounding margin (1e-9) rather than using exact equality.
         */
        private const val EF_TOLERANCE = 1e-9
    }

    // ── Test 1: First review, correct (quality=4, "Good") ───────────────
    //
    // Brand-new card (rep=0, EF=2.5, interval=0). First correct answer.
    // SM-2 says: when n=0 and q≥3, interval becomes 1 day.
    // EF adjustment for q=4 is exactly 0.0, so EF stays at 2.5.
    @Test
    fun testFirstReview_CorrectQuality4_GivesIntervalOf1Day() {
        val result = calculateNextReview(
            quality = 4,
            currentRepetition = 0,
            currentEaseFactor = DEFAULT_EF,
            currentIntervalDays = 0,
            today = TODAY,
        )

        assertEquals(1, result.repetition)
        assertEquals(1, result.intervalDays)
        assertEquals(DEFAULT_EF, result.easeFactor, EF_TOLERANCE)
        assertEquals(TODAY.plusDays(1), result.nextReviewDate)
    }

    // ── Test 2: Second review, correct (quality=4) ──────────────────────
    //
    // After one successful review (rep=1). SM-2 says: when n=1 and q≥3,
    // interval becomes the fixed value of 6 days. This is the second of
    // two "bootstrapping" intervals before exponential growth kicks in.
    @Test
    fun testSecondReview_CorrectQuality4_GivesIntervalOf6Days() {
        val result = calculateNextReview(
            quality = 4,
            currentRepetition = 1,
            currentEaseFactor = DEFAULT_EF,
            currentIntervalDays = 1,
            today = TODAY,
        )

        assertEquals(2, result.repetition)
        assertEquals(6, result.intervalDays)
        assertEquals(DEFAULT_EF, result.easeFactor, EF_TOLERANCE)
        assertEquals(TODAY.plusDays(6), result.nextReviewDate)
    }

    // ── Test 3: Third review, correct (quality=4) ───────────────────────
    //
    // After two successful reviews (rep=2, interval=6). Now the
    // multiplicative formula kicks in: interval = round(6 × 2.5) = 15.
    // This is where spaced repetition truly begins — intervals grow
    // exponentially with each successful recall.
    @Test
    fun testThirdReview_CorrectQuality4_UsesMultiplicativeInterval() {
        val result = calculateNextReview(
            quality = 4,
            currentRepetition = 2,
            currentEaseFactor = DEFAULT_EF,
            currentIntervalDays = 6,
            today = TODAY,
        )

        assertEquals(3, result.repetition)
        assertEquals(15, result.intervalDays) // round(6 × 2.5) = 15
        assertEquals(DEFAULT_EF, result.easeFactor, EF_TOLERANCE)
    }

    // ── Test 4: Easy answer (quality=5) increases EF ────────────────────
    //
    // q=5 is "perfect recall." The EF adjustment is +0.10, so EF goes
    // from 2.5 to 2.6. Future intervals will grow 4% faster because
    // the multiplier is larger. The CURRENT interval still uses the
    // old EF (2.5), so interval = round(6 × 2.5) = 15.
    @Test
    fun testEasyAnswer_Quality5_IncreasesEaseFactor() {
        val result = calculateNextReview(
            quality = 5,
            currentRepetition = 2,
            currentEaseFactor = DEFAULT_EF,
            currentIntervalDays = 6,
            today = TODAY,
        )

        assertEquals(3, result.repetition)
        assertEquals(15, result.intervalDays) // Uses OLD EF: round(6 × 2.5) = 15
        assertEquals(2.6, result.easeFactor, EF_TOLERANCE) // 2.5 + 0.10 = 2.6
    }

    // ── Test 5: Hard answer (quality=3) decreases EF ────────────────────
    //
    // q=3 is "correct with serious difficulty." The EF adjustment is -0.14,
    // so EF drops from 2.5 to 2.36. The CURRENT interval still uses the
    // old EF (2.5): round(6 × 2.5) = 15. The penalty only affects future
    // reviews — next time, the multiplier will be 2.36 instead of 2.5,
    // producing shorter intervals going forward.
    @Test
    fun testHardAnswer_Quality3_DecreasesEaseFactor() {
        val result = calculateNextReview(
            quality = 3,
            currentRepetition = 2,
            currentEaseFactor = DEFAULT_EF,
            currentIntervalDays = 6,
            today = TODAY,
        )

        assertEquals(3, result.repetition)
        assertEquals(15, result.intervalDays) // Uses OLD EF: round(6 × 2.5) = 15
        assertEquals(2.36, result.easeFactor, EF_TOLERANCE) // 2.5 - 0.14 = 2.36
    }

    // ── Test 6: Incorrect answer (quality=1, "Again") resets ────────────
    //
    // q=1 means the user failed to recall the card. Regardless of how
    // well the card was previously known (rep=5, interval=30 days), SM-2
    // resets the repetition count to 0 and the interval to 1 day. The
    // card is essentially "relearned" from scratch.
    //
    // EF is also penalized: 2.5 + (-0.54) = 1.96. This means even after
    // the card is relearned, future intervals will grow more slowly,
    // reflecting the fact that this card is harder for the user.
    @Test
    fun testIncorrectAnswer_ResetsRepetitionAndInterval() {
        val result = calculateNextReview(
            quality = 1,
            currentRepetition = 5,
            currentEaseFactor = DEFAULT_EF,
            currentIntervalDays = 30,
            today = TODAY,
        )

        assertEquals(0, result.repetition) // Reset to 0
        assertEquals(1, result.intervalDays) // Back to 1 day
        assertEquals(1.96, result.easeFactor, EF_TOLERANCE) // 2.5 - 0.54 = 1.96
        assertEquals(TODAY.plusDays(1), result.nextReviewDate)
    }

    // ── Test 7: EF floor at 1.3 ────────────────────────────────────────
    //
    // If a card already has the minimum EF (1.3) and the user answers
    // incorrectly (q=1), the formula would produce 1.3 - 0.54 = 0.76.
    // But SM-2 clamps EF at 1.3 to prevent the multiplier from becoming
    // so small that intervals barely grow (e.g., 10 → 13 → 17 instead of
    // 10 → 25 → 63). Without this floor, the user would be trapped in
    // an unproductive review loop.
    @Test
    fun testEaseFactor_NeverDropsBelow1Point3() {
        val result = calculateNextReview(
            quality = 1,
            currentRepetition = 0,
            currentEaseFactor = 1.3,
            currentIntervalDays = 0,
            today = TODAY,
        )

        assertEquals(0, result.repetition)
        assertEquals(1, result.intervalDays)
        assertEquals(1.3, result.easeFactor, EF_TOLERANCE) // Clamped, not 0.76
    }

    // ── Test 8: Invalid quality throws IllegalArgumentException ─────────
    //
    // The SM-2 algorithm only accepts qualities 0–5. Passing values outside
    // this range indicates a programming error in the caller. Using
    // `require` (internal Rule 2) ensures fast failure with a clear message
    // rather than silently producing nonsense output.
    @Test
    fun testQualityAbove5_ThrowsIllegalArgumentException() {
        assertThrows<IllegalArgumentException> {
            calculateNextReview(
                quality = 6,
                currentRepetition = 0,
                currentEaseFactor = DEFAULT_EF,
                currentIntervalDays = 0,
                today = TODAY,
            )
        }
    }

    @Test
    fun testNegativeQuality_ThrowsIllegalArgumentException() {
        assertThrows<IllegalArgumentException> {
            calculateNextReview(
                quality = -1,
                currentRepetition = 0,
                currentEaseFactor = DEFAULT_EF,
                currentIntervalDays = 0,
                today = TODAY,
            )
        }
    }

    // ── Test 9: Next review date uses the `today` parameter ─────────────
    //
    // Verifies the date arithmetic works with a specific fixed date.
    // This also implicitly proves that the `today` parameter is actually
    // used (and not `LocalDate.now()` internally), which is essential for
    // test determinism.
    @Test
    fun testNextReviewDate_IsTodayPlusIntervalDays() {
        val specificDate = LocalDate.of(2026, 6, 15)

        val result = calculateNextReview(
            quality = 4,
            currentRepetition = 1,
            currentEaseFactor = DEFAULT_EF,
            currentIntervalDays = 1,
            today = specificDate,
        )

        // Second review → interval = 6 days
        assertEquals(LocalDate.of(2026, 6, 21), result.nextReviewDate)
    }

    // ── Test 10: Chain of 5 consecutive correct reviews ─────────────────
    //
    // Simulates a realistic study scenario: a user answers "Good" (q=4)
    // five times in a row on the same card. Since q=4 produces an EF
    // adjustment of exactly 0.0, the EF stays at 2.5 throughout.
    //
    // Expected intervals: 1, 6, 15, 38, 95
    //   Step 1: n=0→1, interval = 1 (fixed first interval)
    //   Step 2: n=1→2, interval = 6 (fixed second interval)
    //   Step 3: n=2→3, interval = round(6 × 2.5) = 15
    //   Step 4: n=3→4, interval = round(15 × 2.5) = 38  (37.5 rounds up)
    //   Step 5: n=4→5, interval = round(38 × 2.5) = 95
    //
    // This demonstrates the exponential growth that makes spaced repetition
    // efficient: after just 5 reviews, the card won't appear for ~3 months.
    @Test
    fun testChainOf5CorrectReviews_ProducesExponentialIntervalGrowth() {
        val expectedIntervals = listOf(1, 6, 15, 38, 95)

        var repetition = 0
        var easeFactor = DEFAULT_EF
        var intervalDays = 0

        expectedIntervals.forEachIndexed { step, expectedInterval ->
            val result = calculateNextReview(
                quality = 4,
                currentRepetition = repetition,
                currentEaseFactor = easeFactor,
                currentIntervalDays = intervalDays,
                today = TODAY,
            )

            assertEquals(expectedInterval, result.intervalDays,
                "Step ${step + 1}: expected interval=$expectedInterval but got ${result.intervalDays}")
            assertEquals(step + 1, result.repetition,
                "Step ${step + 1}: expected repetition=${step + 1} but got ${result.repetition}")
            assertEquals(DEFAULT_EF, result.easeFactor, EF_TOLERANCE,
                "Step ${step + 1}: EF should remain $DEFAULT_EF for q=4")

            // Feed output back as input for next step
            repetition = result.repetition
            easeFactor = result.easeFactor
            intervalDays = result.intervalDays
        }
    }

    // ── Test 11: Incorrect answer after a long streak is devastating ────
    //
    // A card at rep=4 with a 38-day interval is well-learned. But one
    // incorrect answer (q=1) resets it completely: rep→0, interval→1.
    // This is the SM-2 "cliff" — it's harsh but effective, ensuring the
    // user truly knows the material before spacing it out again.
    @Test
    fun testIncorrectAnswer_AfterLongStreak_ResetsToDay1() {
        val result = calculateNextReview(
            quality = 1,
            currentRepetition = 4,
            currentEaseFactor = DEFAULT_EF,
            currentIntervalDays = 38,
            today = TODAY,
        )

        assertEquals(0, result.repetition)
        assertEquals(1, result.intervalDays)
        // EF penalized: 2.5 - 0.54 = 1.96
        assertEquals(1.96, result.easeFactor, EF_TOLERANCE)
    }

    // ── Test 12: Recovery after an incorrect answer ─────────────────────
    //
    // After test 11's reset (rep=0, EF=1.96), the user answers correctly
    // again (q=4). The card starts over with interval=1, but the lowered
    // EF (1.96) means future intervals will grow more slowly than they did
    // before the failure. This is SM-2's way of "remembering" that this
    // card is hard for the user.
    @Test
    fun testRecovery_AfterIncorrect_UsesLoweredEaseFactor() {
        // State after an incorrect answer: rep=0, EF=1.96
        val result = calculateNextReview(
            quality = 4,
            currentRepetition = 0,
            currentEaseFactor = 1.96,
            currentIntervalDays = 1,
            today = TODAY,
        )

        assertEquals(1, result.repetition)
        assertEquals(1, result.intervalDays) // n=0 → fixed interval of 1
        // EF unchanged by q=4 (adjustment is 0.0): 1.96 + 0.0 = 1.96
        assertEquals(1.96, result.easeFactor, EF_TOLERANCE)
    }

    // ── Test 13: Quality 0 (complete blackout) has maximum EF penalty ───
    //
    // q=0 is the worst possible grade. EF adjustment is -0.80.
    // From EF=2.5: 2.5 - 0.80 = 1.70. Repetition and interval reset.
    @Test
    fun testQuality0_AppliesMaximumEaseFactorPenalty() {
        val result = calculateNextReview(
            quality = 0,
            currentRepetition = 3,
            currentEaseFactor = DEFAULT_EF,
            currentIntervalDays = 15,
            today = TODAY,
        )

        assertEquals(0, result.repetition)
        assertEquals(1, result.intervalDays)
        assertEquals(1.7, result.easeFactor, EF_TOLERANCE) // 2.5 - 0.80 = 1.70
    }

    // ── Test 14: Quality 2 is still incorrect (resets) ──────────────────
    //
    // q=2 is below the q≥3 threshold, so it's treated as incorrect:
    // repetition resets to 0, interval resets to 1. The EF adjustment
    // is -0.32: 2.5 - 0.32 = 2.18.
    @Test
    fun testQuality2_IsBelowThreshold_ResetsSchedule() {
        val result = calculateNextReview(
            quality = 2,
            currentRepetition = 3,
            currentEaseFactor = DEFAULT_EF,
            currentIntervalDays = 15,
            today = TODAY,
        )

        assertEquals(0, result.repetition)
        assertEquals(1, result.intervalDays)
        assertEquals(2.18, result.easeFactor, EF_TOLERANCE) // 2.5 - 0.32 = 2.18
    }

    // ── Test 15: Multiplicative interval with non-default EF ────────────
    //
    // Verifies that a lowered EF (e.g., 1.96 after previous failures)
    // produces a shorter interval than the default EF would. With EF=1.96
    // and interval=6: round(6 × 1.96) = round(11.76) = 12.
    // Compare to default EF: round(6 × 2.5) = 15.
    @Test
    fun testMultiplicativeInterval_UsesActualEaseFactor() {
        val result = calculateNextReview(
            quality = 4,
            currentRepetition = 2,
            currentEaseFactor = 1.96,
            currentIntervalDays = 6,
            today = TODAY,
        )

        assertEquals(3, result.repetition)
        assertEquals(12, result.intervalDays) // round(6 × 1.96) = 12
        assertEquals(1.96, result.easeFactor, EF_TOLERANCE) // q=4 → adjustment 0.0
    }
}