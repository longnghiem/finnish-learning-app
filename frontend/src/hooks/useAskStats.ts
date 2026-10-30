import { useQuery } from '@tanstack/react-query'
import { fetchAskStats } from '../api'
import type { AskStats } from '../schemas'

/**
 * React Query hook for the chatbot's public totals (`GET /api/ask/stats`). The backend caches
 * them for 60 s, so asking more often would only repeat the same numbers.
 */
export function useAskStats() {
  return useQuery<AskStats, Error>({
    queryKey: ['ask', 'stats'],
    queryFn: fetchAskStats,
    staleTime: 60_000,
  })
}
