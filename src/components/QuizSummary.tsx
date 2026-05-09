import {useLang} from "../lang";
import {useNavigate} from "react-router-dom";

export interface QuizAnswerLog {
  quality: number
  correct: boolean
}

interface QuizSummaryProps {
  answers: QuizAnswerLog[]
  topicId: number
  onQuizAgain: () => void
}

/**
 * End-of-session quiz summary.
 *
 * Renders the totals computed from the local answer log:
 * - Total cards reviewed
 * - Accuracy (correct / total, where "correct" is quality ≥ 3)
 * - Per-button breakdown (Again / Hard / Good / Easy counts)
 *
 * Two actions are offered: "Quiz again" (refetches the next batch
 * and resets local state via `onQuizAgain`) and "Back to topic"
 * (navigates to the topic browse page).
 *
 * The summary is intentionally derived from local state — the
 * backend has no concept of quiz "sessions" yet, so all aggregation
 * happens client-side from the array of answers collected during
 * the run.
 */
export function QuizSummary({ answers, topicId, onQuizAgain }: QuizSummaryProps) {
  const { L } = useLang()
  const navigate = useNavigate()

  const total = answers.length
  const correctCount = answers.filter(a => a.correct).length
  const accuracyPct = total === 0 ? 0 : Math.round((correctCount / total) * 100)

  const counts = {
    again: answers.filter(a => a.quality === 1).length,
    hard: answers.filter(a => a.quality === 3).length,
    good: answers.filter(a => a.quality === 4).length,
    easy: answers.filter(a => a.quality === 5).length,
  }

  const breakdownRow = (label: string, count: number, colorClass: string) => (
    <div className="flex items-center gap-3">
      <span className={`inline-block w-3 h-3 rounded-full ${colorClass}`} />
      <span className="flex-1 text-sm font-semibold text-text-primary">{label}</span>
      <span className="text-sm font-bold text-text-sub tabular-nums">{count}</span>
    </div>
  )

  return (
    <div className="bg-surface rounded-[20px] p-9 w-full max-w-105 mx-auto shadow-card-hover border border-border flex flex-col gap-5">
      <h2 className="m-0 text-[1.4rem] font-extrabold text-text-primary text-center">
        {L.quizComplete}
      </h2>

      <div className="grid grid-cols-2 gap-3">
        <div className="bg-surface-alt rounded-lg p-4 text-center">
          <p className="text-[0.7rem] uppercase tracking-[0.5px] text-text-muted font-bold m-0 mb-1">
            {L.cardsReviewed}
          </p>
          <p className="text-2xl font-extrabold text-text-primary m-0 tabular-nums">{total}</p>
        </div>
        <div className="bg-surface-alt rounded-lg p-4 text-center">
          <p className="text-[0.7rem] uppercase tracking-[0.5px] text-text-muted font-bold m-0 mb-1">
            {L.accuracy}
          </p>
          <p className="text-2xl font-extrabold text-accent m-0 tabular-nums">{accuracyPct}%</p>
        </div>
      </div>

      <div className="flex flex-col gap-2 bg-surface-alt rounded-lg p-4">
        {breakdownRow(L.again, counts.again, 'bg-red')}
        {breakdownRow(L.hard, counts.hard, 'bg-yellow')}
        {breakdownRow(L.good, counts.good, 'bg-green')}
        {breakdownRow(L.easy, counts.easy, 'bg-accent')}
      </div>

      <div className="flex gap-2">
        <button
          type="button"
          onClick={onQuizAgain}
          className="flex-1 rounded-lg bg-amber text-nav-btn-text px-4 py-2.5 text-[0.85rem] font-extrabold border-none
            cursor-pointer font-[inherit] transition-colors duration-150 hover:bg-amber-hover"
        >
          {L.quizAgain}
        </button>
        <button
          type="button"
          onClick={() => navigate(`/topics/${topicId}`)}
          className="flex-1 rounded-lg bg-surface text-text-primary border border-border px-4 py-2.5 text-[0.85rem]
            font-bold cursor-pointer font-[inherit] transition-colors duration-150"
        >
          {L.backToTopic}
        </button>
      </div>
    </div>
  )
}