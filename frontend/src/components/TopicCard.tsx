import type {TopicResponse} from '../types'
import {Link} from 'react-router-dom'
import {topicImages} from '../assets/topics'

interface TopicCardProps {
  topic: TopicResponse
  /** Number of cards due for review for the authenticated user. Renders a badge when > 0. */
  dueCards?: number
}

export function TopicCard({ topic, dueCards }: TopicCardProps) {
  const showBadge = dueCards !== undefined && dueCards > 0

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
        {showBadge && (
          <span className="absolute top-2.5 right-2.5 z-10 rounded-full bg-red text-white text-[0.7rem] font-bold
            px-2.5 py-0.5 tracking-[0.3px] shadow-card">
            {dueCards} due
          </span>
        )}
      </div>
      <div className="px-4.5 pt-3.5 pb-4">
        <p className="text-[1.05rem] font-bold text-text-primary m-0 text-pretty">
          {topic.name}
        </p>
      </div>
    </Link>
  )
}
