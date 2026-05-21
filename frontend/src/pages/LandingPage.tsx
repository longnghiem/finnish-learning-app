import {useTopicProgress, useTopics} from '../hooks'
import {TopicCard} from '../components/TopicCard.tsx'
import {useLang} from '../lang'
import {useAuth} from "../auth/useAuth.ts";
import {Link} from 'react-router-dom'

export function LandingPage() {
  const { data: topics, isLoading, isError } = useTopics()
  const { isLoggedIn } = useAuth()
  // Only fire the progress query when authenticated
  const { data: topicProgress } = useTopicProgress(isLoggedIn)
  const { L } = useLang()

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <p className="text-[1.1rem] text-text-muted">Loading topics…</p>
      </div>
    )
  }

  if (isError) {
    return (
      <div className="flex items-center justify-center py-20">
        <p className="text-[1.1rem] text-red">Failed to load topics.</p>
      </div>
    )
  }

  const totalCardsAll = topics?.reduce((s, t) => s + t.totalCards, 0) ?? 0

  return (
    <div className="page-enter max-w-275 mx-auto px-6 pt-12 pb-16">
      <h1 className="text-center text-[1.75rem] font-extrabold text-text-primary mb-2">
        {L.chooseATopic}
      </h1>
      {topics && (
        <p className={`text-center text-text-muted text-[0.9rem] font-medium ${isLoggedIn ? 'mb-10' : 'mb-6'}`}>
          {topics.length} {L.topics} · {totalCardsAll} {L.cards}
        </p>
      )}

      {!isLoggedIn && (
        <div className="max-w-[720px] mx-auto mb-9 bg-surface border border-border rounded-xl px-[18px] py-3.5
            flex items-center gap-3.5 shadow-card">
          <div className="shrink-0 w-9 h-9 rounded-[10px] bg-[rgba(255,189,89,0.15)] border border-[rgba(255,189,89,0.4)]
              flex items-center justify-center text-[18px]">
            💡
          </div>
          <p className="text-[13.5px] text-text-sub m-0 leading-[1.55] font-medium flex-1">
            {L.demoHintIntro}{' '}
            <span className="text-text-primary font-bold">{L.demoHintFeatures}</span>?{' '}
            <Link to="/register" className="text-text-primary font-bold underline-offset-2 hover:underline">
              {L.demoHintRegister}
            </Link>
            {L.demoHintOr}{' '}
            <code className="font-mono text-[12.5px] font-bold text-amber bg-surface-alt border border-border
                rounded-[5px] px-[7px] py-px whitespace-nowrap">demo</code>
            {' / '}
            <code className="font-mono text-[12.5px] font-bold text-amber bg-surface-alt border border-border
                rounded-[5px] px-[7px] py-px whitespace-nowrap">123456</code>.
          </p>
        </div>
      )}

      <div className="grid grid-cols-[repeat(auto-fill,minmax(240px,1fr))] gap-5">
        {topics?.map(topic => {
          const due = topicProgress?.find(p => p.topicId === topic.id)?.dueCards
          return <TopicCard key={topic.id} topic={topic} dueCards={due} />
        })}
      </div>
    </div>
  )
}
