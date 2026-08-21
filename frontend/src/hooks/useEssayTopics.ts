import { useQuery } from '@tanstack/react-query'
import { type EssayPrompt, EvaluationError, fetchEssayPrompts } from '../api/evaluation.ts'

/**
 * React Query hook for the essay prompts of one topic
 * (`GET /api/essays/prompts?topicId=`), feeding the essay page dropdown.
 *
 * The topic id is part of the query key — without it, switching topics would serve the
 * previous topic's prompts from cache.
 */
export function useEssayTopics(topicId: number | undefined, enabled: boolean = true) {
  return useQuery<EssayPrompt[], EvaluationError>({
    queryKey: ['essayPrompts', topicId],
    queryFn: () => {
      if (topicId === undefined) {
        throw new EvaluationError('GENERIC', 'topicId is required')
      }
      return fetchEssayPrompts(topicId)
    },
    enabled: enabled && topicId !== undefined,
  })
}
