package me.longng.finnish_learning_backend.service.ask

import me.longng.finnish_learning_backend.config.AskProperties
import me.longng.finnish_learning_backend.domain.ask.DailyAskStats
import me.longng.finnish_learning_backend.persistence.AskSessionRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

/**
 * Deletes old conversations every night: every chat session created more than
 * [AskProperties.Retention.days] days ago, and by cascade its turns (messages, answers, fit
 * reports). The daily totals ([DailyAskStats]) are kept: they hold no personal data.
 *
 * The job runs at [AskProperties.Retention.purgeCron], Helsinki time, on Spring Boot's scheduler
 * thread. If a night is missed (the app was down), the next run deletes everything that is due,
 * because the cutoff is always "now minus the retention period". If one run fails, Spring logs the
 * error and the next night tries again.
 *
 * Logs only the number of sessions deleted, never their content.
 */
@Component
class AskRetentionJob(
    private val sessionRepository: AskSessionRepository,
    properties: AskProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val logger = LoggerFactory.getLogger(AskRetentionJob::class.java)

    private val retention: Duration = Duration.ofDays(properties.retention.days.toLong())

    /** Deletes the sessions older than the retention period. Public, so a test can run it too. */
    @Scheduled(cron = "\${app.ask.retention.purge-cron}", zone = HELSINKI_ZONE)
    fun purge() {
        val cutoff = clock.instant().minus(retention)
        val deleted = sessionRepository.deleteCreatedBefore(cutoff)
        logger.info("Ask retention purge: sessionsDeleted={} cutoff={}", deleted, cutoff)
    }

    private companion object {
        /** The zone of the cron expression. A constant, because annotation values must be. */
        const val HELSINKI_ZONE = "Europe/Helsinki"
    }
}