import {useQuery} from "@tanstack/react-query";
import {type DashboardStats, fetchDashboard, fetchTopicProgress, type TopicProgress} from "../api/progress.ts";

const PROGRESS_QUERY_KEY = ['progress'] as const

/**
 * React Query hook for per-topic progress (`GET /api/progress/topics`).
 *
 * Pass `enabled: isLoggedIn` from the caller — the endpoint requires
 * a JWT and would 401 otherwise.
 */
export function useTopicProgress(enabled: boolean = true) {
  return useQuery<TopicProgress[], Error>({
    queryKey: [...PROGRESS_QUERY_KEY, 'topics'],
    queryFn: fetchTopicProgress,
    staleTime: 30_000,
    enabled,
  })
}

/**
 * React Query hook for the dashboard payload (`GET /api/progress/dashboard`).
 *
 * Single call, returns aggregates + per-topic breakdown.
 */
export function useDashboard(enabled: boolean = true) {
  return useQuery<DashboardStats, Error>({
    queryKey: [...PROGRESS_QUERY_KEY, 'dashboard'],
    queryFn: fetchDashboard,
    staleTime: 30_000,
    enabled,
  })
}