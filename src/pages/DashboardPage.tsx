import {StatCard} from "../components/StatCard.tsx";
import {TopicProgressCard} from "../components/TopicProgressCard.tsx";
import {pageContainerCls, pageTitleCls} from "../styles.ts";
import {useDashboard} from "../hooks/useProgress.ts";
import {useEffect} from "react";
import {useLang} from "../lang";
import {useNavigate} from "react-router-dom";
import {useAuth} from "../auth/useAuth.ts";

/**
 * Progress dashboard.
 *
 * Renders five aggregate stat tiles at the top and a per-topic grid
 * below. All data comes from `GET /api/progress/dashboard`, which
 * returns aggregates + the per-topic breakdown in a single response.
 *
 */
export function DashboardPage() {
  const { L } = useLang()
  const { isLoggedIn } = useAuth()
  const navigate = useNavigate()

  // The endpoint requires auth — bounce unauthenticated visitors instead
  // of showing a 401 error card. Mirrors QuizPage's redirect pattern.
  useEffect(() => {
    if (!isLoggedIn) navigate('/login', { replace: true })
  }, [isLoggedIn, navigate])

  const { data, isLoading, isError, refetch } = useDashboard(isLoggedIn)

  const accuracyPct = data ? Math.round(data.overallAccuracy * 100) : 0

  return (
    <div className={pageContainerCls}>
      <h1 className={pageTitleCls}>{L.progressTitle}</h1>

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
        <div className="flex flex-col gap-8">
          {/* Aggregate stat tiles */}
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-3">
            <StatCard label={L.totalReviews} value={data.totalReviews} />
            <StatCard label={L.overallAccuracy} value={`${accuracyPct}%`} />
            <StatCard label={L.currentStreak} value={data.currentStreak} emoji="🔥" />
            <StatCard label={L.bestStreak} value={data.bestStreak} emoji="⭐" />
            <StatCard
              label={L.cardsDueToday}
              value={data.totalDueCards}
              highlight={data.totalDueCards > 0}
            />
          </div>

          {/* Per-topic grid */}
          <div className="grid grid-cols-[repeat(auto-fill,minmax(260px,1fr))] gap-4">
            {data.topicProgress.map(progress => (
              <TopicProgressCard key={progress.topicId} progress={progress} />
            ))}
          </div>
        </div>
      )}
    </div>
  )
}