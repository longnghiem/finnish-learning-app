import type {TopicResponse} from '../types'
import {Link} from 'react-router-dom'
import {topicImages} from '../assets/topics'
import {useLang} from '../lang'

interface TopicCardProps {
  topic: TopicResponse
  /** Number of cards due for review for the authenticated user. Renders an amber badge when > 0. */
  dueCards?: number
}

export function TopicCard({ topic, dueCards }: TopicCardProps) {
  const { L } = useLang()
  const showDueBadge = dueCards !== undefined && dueCards > 0
  const totalCards = topic.totalCards

  return (
    <Link
      to={`/topics/${topic.id}`}
      className="group block no-underline bg-surface border border-border rounded-2xl overflow-hidden cursor-pointer
       shadow-card hover:shadow-card-hover hover:scale-[1.025] hover:-translate-y-0.5 transition-[box-shadow,transform] duration-200 ease-in-out"
    >
      <div className="relative aspect-4/3 overflow-hidden">
        <img
          src={topicImages[topic.id]}
          alt={topic.name}
          className="w-full h-full object-cover block transition-transform duration-350 ease-in-out group-hover:scale-[1.06]"
        />
        {showDueBadge && (
          <span className="absolute top-2.5 right-2.5 z-10 rounded-full bg-amber text-nav-btn-text text-[11px] font-extrabold
            font-mono px-2.5 py-0.5 tracking-[0.2px] shadow-[0_2px_8px_rgba(0,0,0,0.18)]">
            {dueCards} {L.due}
          </span>
        )}
      </div>
      <div className="px-4.5 pt-3.5 pb-4">
        <div className="flex items-start justify-between gap-2.5">
          <div className="min-w-0 flex-1">
            <p className="text-[1.05rem] font-bold text-text-primary m-0 text-pretty">
              {topic.name}
            </p>
          </div>
          <span className="shrink-0 inline-flex items-baseline gap-[3px] bg-surface-alt border border-border
              rounded-lg px-2.5 py-1 font-mono">
            <span className="text-[13px] font-bold text-text-primary leading-none tabular-nums">{totalCards}</span>
            <span className="text-[10px] font-semibold text-text-muted leading-none">
              {totalCards === 1 ? 'card' : 'cards'}
            </span>
          </span>
        </div>
      </div>
    </Link>
  )
}
