import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AdminAskError,
  type AdminTurnFilters,
  deleteAdminSession,
  fetchAdminDailyStats,
  fetchAdminSession,
  fetchAdminTurns,
} from '../api'
import type { AdminDailyStats, AdminSession } from '../schemas'

/** Every admin chatbot query key starts with this, so one prefix reaches all of them. */
const ADMIN_ASK_KEY = ['admin', 'ask'] as const
const TURNS_KEY = [...ADMIN_ASK_KEY, 'turns'] as const

/**
 * The turn list for the given filters, one page of 50 at a time ("Load more").
 *
 * A React Query infinite query: `data.pages` holds every page loaded so far, and
 * `fetchNextPage()` loads the next one with `offset` = the turns already loaded.
 * `hasNextPage` turns false after a page shorter than the limit. The filters are part of the
 * query key, so changing one starts again from the first page.
 *
 * The data is never older than a minute when the tab is opened (`staleTime`), so new turns show
 * up without a reload.
 */
export function useAdminTurns(filters: AdminTurnFilters) {
  return useInfiniteQuery({
    queryKey: [...TURNS_KEY, filters],
    queryFn: ({ pageParam }) => fetchAdminTurns(filters, pageParam),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.turns.length < lastPage.limit ? undefined : lastPage.offset + lastPage.turns.length,
    staleTime: 60_000,
  })
}

/**
 * One session with all its turns, or nothing while `sessionId` is null. A 404 (the session was
 * deleted or purged) isn't retried: asking again won't bring it back.
 */
export function useAdminSession(sessionId: string | null) {
  return useQuery<AdminSession, Error>({
    queryKey: [...ADMIN_ASK_KEY, 'session', sessionId],
    queryFn: () => fetchAdminSession(sessionId!),
    enabled: sessionId !== null,
    retry: (failureCount, error) =>
      !(error instanceof AdminAskError && error.status === 404) && failureCount < 1,
  })
}

/** The daily totals of the last 30 days, oldest first. */
export function useAdminDailyStats() {
  return useQuery<AdminDailyStats[], Error>({
    queryKey: [...ADMIN_ASK_KEY, 'daily-stats'],
    queryFn: fetchAdminDailyStats,
    staleTime: 60_000,
  })
}

/**
 * Deletes a session. On success, the turn lists (every filter) are marked stale so they load
 * again without its turns, and the session's own cached detail is dropped. The daily totals don't
 * change: the backend keeps them.
 */
export function useDeleteAdminSession() {
  const queryClient = useQueryClient()

  return useMutation<void, Error, string>({
    mutationFn: (sessionId) => deleteAdminSession(sessionId),
    onSuccess: async (_, sessionId) => {
      queryClient.removeQueries({ queryKey: [...ADMIN_ASK_KEY, 'session', sessionId] })
      await queryClient.invalidateQueries({ queryKey: TURNS_KEY })
    },
  })
}
