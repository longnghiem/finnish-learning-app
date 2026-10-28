package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.persistence.AskDailyStatsRepository
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The public totals of the recruiter chatbot, shown on the "How this works" panel.
 *
 * The numbers come from the daily totals ([AskDailyStatsRepository]), which hold no personal
 * data. They are kept in memory for [CACHE_TTL]: the page is public, and a visitor reloading it
 * shouldn't cause a database query each time. Being up to a minute behind doesn't matter here.
 */
@Service
class AskStatsService(
    private val statsRepository: AskDailyStatsRepository,
    private val budgetService: BudgetService,
    private val clock: Clock = Clock.system(HELSINKI_ZONE),
) {
    // @Volatile: every thread sees the latest value. Two requests may both find the cache old and
    // both load the numbers; that costs one extra query and is harmless.
    @Volatile
    private var cached: CachedStats? = null

    /** The current totals, at most [CACHE_TTL] old. */
    fun current(): AskStats {
        val now = clock.instant()
        cached?.takeIf { now < it.validUntil }?.let { return it.stats }
        val stats = load()
        cached = CachedStats(stats, validUntil = now.plus(CACHE_TTL))
        return stats
    }

    private fun load(): AskStats {
        val today = LocalDate.ofInstant(clock.instant(), HELSINKI_ZONE)
        val days = statsRepository.findByDayRange(FIRST_DAY, today)
        return AskStats(
            answersTotal = days.sumOf { it.answers.toLong() + it.fitChecks },
            blockedTotal = days.sumOf { it.blocked.toLong() },
            // Already capped at 100 by BudgetService: the month can end a little over its cap.
            budgetUsedPercentThisMonth = budgetService.usage().monthlyPercent,
        )
    }

    private class CachedStats(val stats: AskStats, val validUntil: Instant)

    private companion object {
        val HELSINKI_ZONE: ZoneId = ZoneId.of("Europe/Helsinki")

        /** How long the totals are reused before they are read again. */
        val CACHE_TTL: Duration = Duration.ofSeconds(60)

        /** Earlier than any day the chatbot can have been used, so "since the start" means all rows. */
        val FIRST_DAY: LocalDate = LocalDate.EPOCH
    }
}

/**
 * The public totals of the recruiter chatbot.
 *
 * @property answersTotal chat answers plus fit checks, since the start
 * @property blockedTotal messages the input guardrail refused, since the start
 * @property budgetUsedPercentThisMonth the share of this month's budget used so far, 0 to 100
 */
data class AskStats(
    val answersTotal: Long,
    val blockedTotal: Long,
    val budgetUsedPercentThisMonth: Int,
)