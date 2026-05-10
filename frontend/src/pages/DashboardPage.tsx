import {StatCard} from "../components/StatCard.tsx";
import {
  dashCardCls,
  dashEyebrowCls,
  dashHeaderCls,
  dashMetaTextCls,
  dashPageCls,
  dashPrimaryBtnCls,
  dashRowBtnCls,
  dashSectionLabelCls,
  dashStatGridCls,
  dashSubtitleCls,
  dashTableColsCls,
  dashTableHeaderCellCls,
  dashTitleCls,
} from "../styles.ts";
import {useDashboard} from "../hooks";
import {useEffect} from "react";
import {useLang} from "../lang";
import {useNavigate} from "react-router-dom";
import {useAuth} from "../auth/useAuth.ts";
import type {TopicProgress} from "../api";

/**
 * Linear-style progress dashboard.
 *
 * Renders the header, five aggregate stat tiles, and a
 * per-topic table. All data comes from `GET /api/progress/dashboard`.
 */
export function DashboardPage() {
  const {L} = useLang()
  const {isLoggedIn} = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    if (!isLoggedIn) navigate('/login', {replace: true})
  }, [isLoggedIn, navigate])

  const {data, isLoading, isError, refetch} = useDashboard(isLoggedIn)

  const accuracyPct = data ? Math.round(data.overallAccuracy * 100) : 0

  return (
    <div className={dashPageCls}>
      <div className={dashHeaderCls}>
        <div>
          <p className={dashEyebrowCls}>{L.dashboard}</p>
          <h1 className={dashTitleCls}>{L.progressTitle}</h1>
          <p className={dashSubtitleCls}>{L.dashSubtitle}</p>
        </div>
        <button type="button" onClick={() => navigate('/')} className={dashPrimaryBtnCls}>
          {L.startQuiz} →
        </button>
      </div>

      {isLoading && (
        <div className="flex items-center justify-center py-20">
          <p className="text-[1.1rem] text-text-muted">Loading…</p>
        </div>
      )}

      {isError && (
        <div className="flex flex-col items-center gap-4 py-20">
          <p className="text-[1.1rem] text-red">Failed to load dashboard.</p>
          <button
            type="button"
            onClick={() => void refetch()}
            className="rounded-lg bg-amber text-nav-btn-text px-4 py-2 text-[0.8rem] font-extrabold border-none
              cursor-pointer font-[inherit] transition-colors duration-150 hover:bg-amber-hover"
          >
            Retry
          </button>
        </div>
      )}

      {!isLoading && !isError && data && data.totalReviews === 0 && (
        <div className="flex flex-col items-center gap-4 py-20">
          <p className="text-[1.1rem] text-text-primary font-semibold text-center">
            {L.noProgressYet}
          </p>
          <button
            type="button"
            onClick={() => navigate('/')}
            className="rounded-lg bg-amber text-nav-btn-text px-5 py-2.5 text-[0.85rem] font-extrabold border-none
              cursor-pointer font-[inherit] transition-colors duration-150 hover:bg-amber-hover"
          >
            {L.chooseATopic}
          </button>
        </div>
      )}

      {!isLoading && !isError && data && data.totalReviews > 0 && (
        <>
          <div className={dashStatGridCls}>
            <StatCard
              label={L.totalReviews}
              value={data.totalReviews}
              sub={`${data.correctReviews} ${L.correct}`}
            />
            <StatCard
              label={L.overallAccuracy}
              value={accuracyPct}
              suffix="%"
              sub={`${data.correctReviews} / ${data.totalReviews}`}
              accent={accuracyPct >= 80 ? '↗ strong' : undefined}
            />
            <StatCard
              label={L.currentStreak}
              value={data.currentStreak}
              suffix=""
              sub={`🔥 ${L.active}`}
            />
            <StatCard
              label={L.bestStreak}
              value={data.bestStreak}
              suffix=""
              sub={`⭐ ${L.allTime}`}
            />
            <StatCard
              label={L.cardsDueToday}
              value={data.totalDueCards}
              sub={data.totalDueCards > 0 ? L.due : '—'}
              highlight={data.totalDueCards > 0}
            />
          </div>

          <TopicTable rows={data.topicProgress} />
        </>
      )}
    </div>
  )
}

interface TopicTableProps {
  rows: TopicProgress[]
}

function TopicTable({rows}: TopicTableProps) {
  const {L} = useLang()
  const navigate = useNavigate()

  return (
    <div className={`${dashCardCls} px-6 pt-5 pb-2 overflow-x-auto`}>
      <div className="flex items-baseline justify-between mb-1.5 min-w-[760px]">
        <span className={dashSectionLabelCls}>{L.masteryByTopic}</span>
        <span className={dashMetaTextCls}>{rows.length} {L.topics}</span>
      </div>

      <div className="min-w-[760px]">
        <div className={`${dashTableColsCls} pt-1.5 pb-2.5`}>
          <span className={dashTableHeaderCellCls}>{L.colTopic}</span>
          <span className={`${dashTableHeaderCellCls} text-right`}>{L.colLearned}</span>
          <span className={dashTableHeaderCellCls}>{L.colProgress}</span>
          <span className={`${dashTableHeaderCellCls} text-right`}>{L.colPct}</span>
          <span className={`${dashTableHeaderCellCls} text-right`}>{L.colAccStreak}</span>
          <span className={`${dashTableHeaderCellCls} text-right`}>{L.colDueToday}</span>
        </div>

        {rows.map(t => {
          const learnedPct = t.totalCards > 0 ? Math.round((t.learnedCards / t.totalCards) * 100) : 0
          const accPct = Math.round(t.accuracy * 100)
          return (
            <button
              key={t.topicId}
              type="button"
              onClick={() => navigate(`/quiz/${t.topicId}`)}
              className={`${dashTableColsCls} ${dashRowBtnCls} w-full`}
            >
              <span className="text-[13.5px] font-semibold text-text-primary truncate">
                {t.topicName}
              </span>
              <span className="text-[11.5px] font-mono text-text-muted text-right tabular-nums">
                {t.learnedCards} / {t.totalCards}
              </span>
              <div className="h-1.5 bg-surface-alt rounded-[3px] overflow-hidden">
                <div
                  className="h-full bg-text-primary rounded-[3px] transition-[width] duration-300"
                  style={{width: `${learnedPct}%`}}
                />
              </div>
              <span className="text-[12.5px] font-mono font-semibold text-text-primary text-right tabular-nums">
                {learnedPct}%
              </span>
              <div className="flex flex-col items-end gap-0.5 leading-tight">
                <span className="text-[11.5px] font-mono text-text-muted tabular-nums">{accPct}%</span>
                <span className="text-[11.5px] font-mono text-text-muted whitespace-nowrap tabular-nums">
                  {t.currentStreak}🔥
                </span>
              </div>
              <span
                className={`text-[11.5px] font-mono font-semibold text-right whitespace-nowrap truncate tabular-nums ${
                  t.dueCards > 0 ? 'text-amber' : 'text-text-muted'
                }`}
              >
                {t.dueCards > 0 ? `${t.dueCards} ${L.due}` : '—'}
              </span>
            </button>
          )
        })}
      </div>
    </div>
  )
}