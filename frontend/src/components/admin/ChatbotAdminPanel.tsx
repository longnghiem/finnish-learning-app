import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useLang } from '../../lang'
import type { AdminTurnFilters } from '../../api'
import { useAdminDailyStats, useAdminTurns } from '../../hooks'
import type { AdminDailyStats, AdminTurn, AskMode, TurnStatus } from '../../schemas'
import {
  adminTableWrapCls,
  adminTdCls,
  adminThCls,
  evalPanelIntroCls,
  evalPanelTitleCls,
  finePrintCls,
  inputCls,
  labelCls,
  secondaryButtonCls,
  statusErrorTextCls,
  statusMutedTextCls,
} from '../../styles.ts'
import { AskTurnTable } from './AskTurnTable.tsx'
import { AskSessionDetail } from './AskSessionDetail.tsx'
import { formatUsd } from './adminAskFormat.ts'

const MODES: readonly AskMode[] = ['CHAT', 'FIT_CHECK']
const STATUSES: readonly TurnStatus[] = ['ANSWERED', 'BLOCKED', 'CAPPED', 'ERROR', 'PENDING']

/** The URL parameter of the open session: `/admin?tab=chatbot&session=<id>`. */
const SESSION_PARAM = 'session'

/**
 * The "Chatbot" tab of the admin page: what the recruiter chatbot stored, for the owner.
 *
 * - Without a session in the URL: the daily totals of the last 30 days, then the turns, newest
 *   first, with filters and "Load more".
 * - With `?session=<id>`: that session's whole conversation ({@link AskSessionDetail}), where it
 *   can be deleted. The id is in the URL, so the browser's Back button returns to the list, and a
 *   session can be linked to.
 *
 * The filters stay while a session is open, because this component stays mounted; React Query
 * also keeps the loaded pages, so going back shows the list at once.
 */
export function ChatbotAdminPanel() {
  const [searchParams, setSearchParams] = useSearchParams()
  const sessionId = searchParams.get(SESSION_PARAM)
  const [filters, setFilters] = useState<AdminTurnFilters>({})

  const openSession = (id: string) => setSearchParams({ tab: 'chatbot', [SESSION_PARAM]: id })
  const closeSession = () => setSearchParams({ tab: 'chatbot' })

  if (sessionId !== null) {
    return <AskSessionDetail key={sessionId} sessionId={sessionId} onBack={closeSession} />
  }
  return (
    <div className="flex flex-col gap-8">
      <DailyStatsSection />
      <TurnsSection filters={filters} onFiltersChange={setFilters} onOpen={openSession} />
    </div>
  )
}

/** The turns, newest first: filters, the table, and "Load more". */
function TurnsSection({
  filters,
  onFiltersChange,
  onOpen,
}: {
  filters: AdminTurnFilters
  onFiltersChange: (filters: AdminTurnFilters) => void
  onOpen: (sessionId: string) => void
}) {
  const { L } = useLang()
  const { data, error, isPending, hasNextPage, fetchNextPage, isFetchingNextPage } =
    useAdminTurns(filters)
  const turns = uniqueTurns(data?.pages.flatMap((page) => page.turns) ?? [])

  return (
    <section aria-labelledby="admin-ask-turns-title" className="flex flex-col gap-3">
      <div className="flex flex-col gap-1">
        <h2 id="admin-ask-turns-title" className={evalPanelTitleCls}>
          {L.adminAskTurnsTitle}
        </h2>
        <p className={evalPanelIntroCls}>{L.adminAskTurnsIntro}</p>
      </div>

      <TurnFilters filters={filters} onChange={onFiltersChange} />

      <div className={adminTableWrapCls}>
        {isPending ? (
          <p className={`${statusMutedTextCls} p-8 text-center`}>{L.adminAskLoading}</p>
        ) : error ? (
          <p className={`${statusErrorTextCls} p-8 text-center`}>{error.message}</p>
        ) : turns.length === 0 ? (
          <p className={`${statusMutedTextCls} p-8 text-center`}>{L.adminAskNoTurns}</p>
        ) : (
          <AskTurnTable turns={turns} onOpen={onOpen} />
        )}
      </div>

      {turns.length > 0 && (
        <div className="flex items-center justify-between gap-3 flex-wrap">
          <p className={finePrintCls}>{L.adminAskShown(turns.length)}</p>
          {hasNextPage && (
            <button
              type="button"
              onClick={() => void fetchNextPage()}
              disabled={isFetchingNextPage}
              className={secondaryButtonCls}
            >
              {isFetchingNextPage ? L.adminAskLoading : L.adminAskLoadMore}
            </button>
          )}
        </div>
      )}
    </section>
  )
}

/**
 * The turns without repeats, first occurrence kept. Pages are fetched by offset, newest first: a
 * turn stored between two page loads moves every older turn down by one, so the last turn of one
 * page comes again at the top of the next.
 */
function uniqueTurns(turns: readonly AdminTurn[]): AdminTurn[] {
  const seen = new Set<number>()
  return turns.filter((turn) => {
    if (seen.has(turn.id)) return false
    seen.add(turn.id)
    return true
  })
}

/**
 * The filters of the turn list. Each change applies at once. A date range with `from` after `to`
 * isn't sent: the backend would answer 400, so the form says so instead.
 */
function TurnFilters({
  filters,
  onChange,
}: {
  filters: AdminTurnFilters
  onChange: (filters: AdminTurnFilters) => void
}) {
  const { L } = useLang()
  const [from, setFrom] = useState(filters.from ?? '')
  const [to, setTo] = useState(filters.to ?? '')
  const rangeInvalid = from !== '' && to !== '' && from > to

  const changeDates = (nextFrom: string, nextTo: string) => {
    setFrom(nextFrom)
    setTo(nextTo)
    if (nextFrom !== '' && nextTo !== '' && nextFrom > nextTo) return
    onChange({ ...filters, from: nextFrom || undefined, to: nextTo || undefined })
  }

  const clear = () => {
    setFrom('')
    setTo('')
    onChange({})
  }

  const selectCls = `${inputCls} cursor-pointer`

  return (
    <div className="flex flex-col gap-2">
      <div className="grid grid-cols-2 sm:grid-cols-[repeat(4,minmax(0,1fr))_auto] gap-3 items-end">
        <label className="flex flex-col">
          <span className={labelCls}>{L.adminAskMode}</span>
          <select
            value={filters.mode ?? ''}
            onChange={(e) =>
              onChange({ ...filters, mode: (e.target.value || undefined) as AskMode | undefined })
            }
            className={selectCls}
          >
            <option value="">{L.adminAskFilterAll}</option>
            {MODES.map((mode) => (
              <option key={mode} value={mode}>
                {mode === 'CHAT' ? L.adminAskModeChat : L.adminAskModeFit}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col">
          <span className={labelCls}>{L.adminAskStatus}</span>
          <select
            value={filters.status ?? ''}
            onChange={(e) =>
              onChange({
                ...filters,
                status: (e.target.value || undefined) as TurnStatus | undefined,
              })
            }
            className={selectCls}
          >
            <option value="">{L.adminAskFilterAll}</option>
            {STATUSES.map((status) => (
              <option key={status} value={status}>
                {L.adminAskStatuses[status]}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col">
          <span className={labelCls}>{L.adminAskFilterFrom}</span>
          <input
            type="date"
            value={from}
            onChange={(e) => changeDates(e.target.value, to)}
            aria-invalid={rangeInvalid}
            className={inputCls}
          />
        </label>
        <label className="flex flex-col">
          <span className={labelCls}>{L.adminAskFilterTo}</span>
          <input
            type="date"
            value={to}
            onChange={(e) => changeDates(from, e.target.value)}
            aria-invalid={rangeInvalid}
            className={inputCls}
          />
        </label>
        <button
          type="button"
          onClick={clear}
          className={`${secondaryButtonCls} col-span-2 sm:col-span-1`}
        >
          {L.adminAskClearFilters}
        </button>
      </div>
      {rangeInvalid && <p className={statusErrorTextCls}>{L.adminAskRangeInvalid}</p>}
    </div>
  )
}

/** The daily totals of the last 30 days, newest first, with a total row. */
function DailyStatsSection() {
  const { L } = useLang()
  const { data, error, isPending } = useAdminDailyStats()
  const days = data ? [...data].reverse() : []
  const total = days.reduce<AdminDailyStats>(
    (sum, day) => ({
      day: '',
      answers: sum.answers + day.answers,
      fitChecks: sum.fitChecks + day.fitChecks,
      blocked: sum.blocked + day.blocked,
      spentUsd: sum.spentUsd + day.spentUsd,
      reservedUsd: sum.reservedUsd + day.reservedUsd,
    }),
    { day: '', answers: 0, fitChecks: 0, blocked: 0, spentUsd: 0, reservedUsd: 0 },
  )

  return (
    <section aria-labelledby="admin-ask-daily-title" className="flex flex-col gap-3">
      <h2 id="admin-ask-daily-title" className={evalPanelTitleCls}>
        {L.adminAskDailyTitle}
      </h2>
      <div className={adminTableWrapCls}>
        {isPending ? (
          <p className={`${statusMutedTextCls} p-8 text-center`}>{L.adminAskLoading}</p>
        ) : error ? (
          <p className={`${statusErrorTextCls} p-8 text-center`}>{error.message}</p>
        ) : days.length === 0 ? (
          <p className={`${statusMutedTextCls} p-8 text-center`}>{L.adminAskDailyEmpty}</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse">
              <thead>
                <tr>
                  <th className={adminThCls}>{L.adminAskDay}</th>
                  <th className={`${adminThCls} text-right`}>{L.adminAskAnswers}</th>
                  <th className={`${adminThCls} text-right`}>{L.adminAskFitChecks}</th>
                  <th className={`${adminThCls} text-right`}>{L.adminAskBlocked}</th>
                  <th className={`${adminThCls} text-right`}>{L.adminAskSpent}</th>
                  <th className={`${adminThCls} text-right`}>{L.adminAskReserved}</th>
                </tr>
              </thead>
              <tbody>
                {days.map((day) => (
                  <DailyStatsRow key={day.day} label={day.day} stats={day} />
                ))}
                <DailyStatsRow label={L.adminAskTotal} stats={total} strong />
              </tbody>
            </table>
          </div>
        )}
      </div>
    </section>
  )
}

/** One row of the daily totals; `strong` for the total row. */
function DailyStatsRow({
  label,
  stats,
  strong = false,
}: {
  label: string
  stats: AdminDailyStats
  strong?: boolean
}) {
  const cell = `${adminTdCls} font-mono tabular-nums ${strong ? 'font-bold bg-surface-alt' : ''}`
  return (
    <tr>
      <td className={cell}>{label}</td>
      <td className={`${cell} text-right`}>{stats.answers}</td>
      <td className={`${cell} text-right`}>{stats.fitChecks}</td>
      <td className={`${cell} text-right`}>{stats.blocked}</td>
      <td className={`${cell} text-right`}>{formatUsd(stats.spentUsd)}</td>
      <td className={`${cell} text-right ${stats.reservedUsd > 0 ? 'text-red' : ''}`}>
        {formatUsd(stats.reservedUsd)}
      </td>
    </tr>
  )
}
