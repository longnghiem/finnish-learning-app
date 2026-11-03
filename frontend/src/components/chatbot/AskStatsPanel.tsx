import { useEffect } from 'react'
import { useLang } from '../../lang'
import { useAskStats } from '../../hooks'
import { evalPanelTitleCls } from '../../styles.ts'
import { StatCard } from '../StatCard.tsx'

/** Shown in a tile while the numbers load. */
const PLACEHOLDER = '—'

/**
 * The chatbot's live numbers from {@link useAskStats}: answers given, attacks blocked, and how
 * much of this month's budget is used. The budget shows as a percentage only: the amount is a
 * server setting and would go out of date here.
 *
 * - While the numbers load, each tile shows a dash.
 * - If they can't be loaded, the panel isn't shown at all: the page works without it, and an
 *   error box about statistics would only distract. The failure is logged with `console.warn`.
 */
export function AskStatsPanel() {
  const { lang, L } = useLang()
  const { data, error } = useAskStats()

  useEffect(() => {
    if (error) console.warn('[AskStatsPanel] Stats could not be loaded', { message: error.message })
  }, [error])

  if (error) return null

  const locale = lang === 'fi' ? 'fi-FI' : 'en-GB'
  const count = (n: number | undefined) =>
    n === undefined ? PLACEHOLDER : n.toLocaleString(locale)

  return (
    <section aria-labelledby="ask-stats-title" className="flex flex-col gap-3">
      <h2 id="ask-stats-title" className={evalPanelTitleCls}>
        {L.askStatsTitle}
      </h2>
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <StatCard
          label={L.askStatsAnswers}
          value={count(data?.answersTotal)}
          sub={L.askStatsAnswersSub}
        />
        <StatCard
          label={L.askStatsBlocked}
          value={count(data?.attacksBlockedTotal)}
          sub={L.askStatsBlockedSub}
        />
        <StatCard
          label={L.askStatsBudget}
          value={count(data?.budgetUsedPercentThisMonth)}
          suffix={data ? '%' : undefined}
          sub={L.askStatsBudgetSub}
        />
      </div>
    </section>
  )
}
