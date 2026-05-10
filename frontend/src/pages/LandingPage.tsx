import {useTopicProgress, useTopics} from '../hooks'
import {TopicCard} from '../components/TopicCard.tsx'
import {useLang} from '../lang'
import {useAuth} from "../auth/useAuth.ts";

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

  return (
    <div className="page-enter max-w-275 mx-auto px-6 pt-12 pb-16">
      <h1 className="text-center text-[1.75rem] font-extrabold text-text-primary mb-2">
        {L.chooseATopic}
      </h1>
      {topics && (
        <p className="text-center text-text-muted text-[0.9rem] mb-10 font-medium">
          {topics.length} topics
        </p>
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