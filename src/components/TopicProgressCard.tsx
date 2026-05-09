import type {TopicProgress} from "../api/progress.ts";
import {useLang} from "../lang";
import {Link} from "react-router-dom";

interface TopicProgressCardProps {
  progress: TopicProgress
}

/**
 * Per-topic row on the dashboard.
 *
 * Renders: name, learned/total bar, due-cards badge (only when > 0),
 * accuracy %, current streak, and a "Start Quiz" button linking to
 * the quiz page for this topic.
 */
export function TopicProgressCard({ progress }: TopicProgressCardProps) {
  const { L } = useLang()

  const learnedPct = progress.totalCards > 0
    ? (progress.learnedCards / progress.totalCards) * 100
    : 0
  const accuracyPct = Math.round(progress.accuracy * 100)

  return (
    <div className="bg-surface border border-border rounded-2xl p-5 shadow-card flex flex-col gap-3.5">
      <div className="flex items-start justify-between gap-3">
        <h3 className="text-[1.05rem] font-extrabold text-text-primary m-0 [text-wrap:pretty]">
          {progress.topicName}
        </h3>
        {progress.dueCards > 0 && (
          <span className="shrink-0 rounded-full bg-red text-white text-[0.7rem] font-bold px-2.5 py-0.5 tracking-[0.3px]">
            {progress.dueCards} {L.due}
          </span>
        )}
      </div>

      <div className="flex flex-col gap-1">
        <div className="h-2 w-full rounded-full bg-border overflow-hidden">
          <div
            className="h-full bg-accent transition-[width] duration-200"
            style={{ width: `${learnedPct}%` }}
          />
        </div>
        <span className="text-[0.75rem] text-text-muted font-semibold">
          {L.learned}: {progress.learnedCards} / {progress.totalCards}
        </span>
      </div>

      <div className="grid grid-cols-2 gap-2 text-[0.78rem]">
        <div className="flex items-center justify-between bg-surface-alt rounded-md px-2.5 py-1.5">
          <span className="text-text-muted font-semibold">{L.accuracy}</span>
          <span className="text-text-primary font-extrabold tabular-nums">{accuracyPct}%</span>
        </div>
        <div className="flex items-center justify-between bg-surface-alt rounded-md px-2.5 py-1.5">
          <span className="text-text-muted font-semibold">{L.currentStreak}</span>
          <span className="text-text-primary font-extrabold tabular-nums">
            {progress.currentStreak}🔥
          </span>
        </div>
      </div>

      <Link
        to={`/quiz/${progress.topicId}`}
        className="mt-1 rounded-lg bg-amber text-nav-btn-text px-3.5 py-2 text-[0.8rem] font-extrabold no-underline
          text-center cursor-pointer transition-colors duration-150 hover:bg-amber-hover"
      >
        {L.startQuiz}
      </Link>
    </div>
  )
}