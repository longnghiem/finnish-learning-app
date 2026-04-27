import type {TopicResponse} from '../types'
import {Link} from 'react-router-dom'
import {topicImages} from '../assets/topics'

interface TopicCardProps {
  topic: TopicResponse
}

export function TopicCard({ topic }: TopicCardProps) {
  return (
    <Link
      to={`/topics/${topic.id}`}
      className="group block no-underline bg-surface border border-border rounded-2xl overflow-hidden cursor-pointer
       shadow-card hover:shadow-card-hover hover:scale-[1.025] hover:-translate-y-0.5 transition-[box-shadow,transform] duration-200 ease-in-out"
    >
      <div className="aspect-[4/3] overflow-hidden">
        <img
          src={topicImages[topic.id]}
          alt={topic.name}
          className="w-full h-full object-cover block transition-transform duration-[350ms] ease-in-out group-hover:scale-[1.06]"
        />
      </div>
      <div className="px-[18px] pt-3.5 pb-4">
        <p className="text-[1.05rem] font-bold text-text-primary m-0 [text-wrap:pretty]">
          {topic.name}
        </p>
      </div>
    </Link>
  )
}
