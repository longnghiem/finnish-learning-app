package me.longng.finnish_learning_backend.persistence

import me.longng.finnish_learning_backend.domain.ask.DailyAskStats
import me.longng.finnish_learning_backend.persistence.generated.tables.records.AskDailyStatsRecord
import me.longng.finnish_learning_backend.persistence.generated.tables.references.ASK_DAILY_STATS
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.math.BigDecimal
import java.time.LocalDate

@Repository
class AskDailyStatsRepositoryImpl(
    private val dsl: DSLContext,
) : AskDailyStatsRepository {

    override fun acquireBudgetLock() {
        check(TransactionSynchronizationManager.isActualTransactionActive()) {
            "acquireBudgetLock() must run inside a transaction"
        }
        dsl.fetch("select pg_advisory_xact_lock({0})", DSL.inline(BUDGET_LOCK_KEY))
    }

    override fun addReservation(day: LocalDate, amountUsd: BigDecimal) {
        require(amountUsd.signum() >= 0) { "amountUsd must not be negative" }
        dsl.insertInto(ASK_DAILY_STATS)
            .set(ASK_DAILY_STATS.DAY, day)
            .set(ASK_DAILY_STATS.RESERVED_USD, amountUsd)
            .onConflict(ASK_DAILY_STATS.DAY)
            .doUpdate()
            .set(ASK_DAILY_STATS.RESERVED_USD, ASK_DAILY_STATS.RESERVED_USD.plus(amountUsd))
            .execute()
    }

    override fun settleReservation(day: LocalDate, reservedUsd: BigDecimal, actualUsd: BigDecimal) {
        require(reservedUsd.signum() >= 0) { "reservedUsd must not be negative" }
        require(actualUsd.signum() >= 0) { "actualUsd must not be negative" }
        val updated = dsl.update(ASK_DAILY_STATS)
            .set(ASK_DAILY_STATS.RESERVED_USD, ASK_DAILY_STATS.RESERVED_USD.minus(reservedUsd))
            .set(ASK_DAILY_STATS.SPENT_USD, ASK_DAILY_STATS.SPENT_USD.plus(actualUsd))
            .where(ASK_DAILY_STATS.DAY.eq(day))
            .execute()
        check(updated == 1) { "No budget row for $day: nothing was reserved that day" }
    }

    override fun incrementCounters(day: LocalDate, answers: Int, fitChecks: Int, blocked: Int) {
        require(answers >= 0 && fitChecks >= 0 && blocked >= 0) { "Increments must not be negative" }
        dsl.insertInto(ASK_DAILY_STATS)
            .set(ASK_DAILY_STATS.DAY, day)
            .set(ASK_DAILY_STATS.ANSWERS, answers)
            .set(ASK_DAILY_STATS.FIT_CHECKS, fitChecks)
            .set(ASK_DAILY_STATS.BLOCKED, blocked)
            .onConflict(ASK_DAILY_STATS.DAY)
            .doUpdate()
            .set(ASK_DAILY_STATS.ANSWERS, ASK_DAILY_STATS.ANSWERS.plus(answers))
            .set(ASK_DAILY_STATS.FIT_CHECKS, ASK_DAILY_STATS.FIT_CHECKS.plus(fitChecks))
            .set(ASK_DAILY_STATS.BLOCKED, ASK_DAILY_STATS.BLOCKED.plus(blocked))
            .execute()
    }

    override fun findByDay(day: LocalDate): DailyAskStats? {
        return dsl.selectFrom(ASK_DAILY_STATS)
            .where(ASK_DAILY_STATS.DAY.eq(day))
            .fetchOne()
            ?.toDomain()
    }

    override fun sumSpentAndReservedUsd(from: LocalDate, to: LocalDate): BigDecimal {
        require(!from.isAfter(to)) { "from must not be after to" }
        return dsl.select(
            DSL.coalesce(DSL.sum(ASK_DAILY_STATS.SPENT_USD.plus(ASK_DAILY_STATS.RESERVED_USD)), BigDecimal.ZERO),
        )
            .from(ASK_DAILY_STATS)
            .where(ASK_DAILY_STATS.DAY.between(from, to))
            .fetchSingle()
            .value1()
    }

    override fun findByDayRange(from: LocalDate, to: LocalDate): List<DailyAskStats> {
        require(!from.isAfter(to)) { "from must not be after to" }
        return dsl.selectFrom(ASK_DAILY_STATS)
            .where(ASK_DAILY_STATS.DAY.between(from, to))
            .orderBy(ASK_DAILY_STATS.DAY.asc())
            .fetch()
            .map { it.toDomain() }
    }

    private fun AskDailyStatsRecord.toDomain() = DailyAskStats(
        day = requireNotNull(day),
        answers = requireNotNull(answers),
        fitChecks = requireNotNull(fitChecks),
        blocked = requireNotNull(blocked),
        spentUsd = requireNotNull(spentUsd),
        reservedUsd = requireNotNull(reservedUsd),
    )

    private companion object {
        /**
         * Key of the advisory lock taken by [acquireBudgetLock]. Any fixed number works, as long as no
         * other code uses it for a different lock: this one is the ASCII bytes of "ASK_BUDG".
         */
        const val BUDGET_LOCK_KEY = 0x41534B5F42554447L
    }
}