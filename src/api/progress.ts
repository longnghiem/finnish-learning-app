import {API_BASE_URL, getAuthHeaders} from "./config.ts";
import type {ErrorResponse} from "../types";

/**
 * Per-topic progress for the authenticated user.
 *
 * Mirrors the backend `TopicProgressResponse`. `accuracy` is a fraction
 * in [0.0, 1.0]; display layers format it as a percentage.
 */
export interface TopicProgress {
  topicId: number
  topicName: string
  totalCards: number
  learnedCards: number
  dueCards: number
  accuracy: number
  currentStreak: number
  bestStreak: number
}

/**
 * Aggregated dashboard for the authenticated user.
 *
 * Mirrors the backend `DashboardResponse`. `topicProgress` is the
 * per-topic breakdown the dashboard renders below the aggregates.
 *
 * `currentStreak` and `bestStreak` here are the max across topics
 */
export interface DashboardStats {
  totalReviews: number
  correctReviews: number
  overallAccuracy: number
  currentStreak: number
  bestStreak: number
  totalDueCards: number
  topicProgress: TopicProgress[]
}

/**
 * Fetches per-topic progress for the authenticated user.
 *
 * Returns one entry per topic, including topics the user has never
 * touched (counters all zero).
 */
export async function fetchTopicProgress(): Promise<TopicProgress[]> {
  const response = await fetch(
    `${API_BASE_URL}/api/progress/topics`,
    { headers: { ...getAuthHeaders() } },
  )

  if (!response.ok) {
    const err: ErrorResponse = await response.json()
    throw new Error(err.message)
  }

  return response.json()
}

/**
 * Fetches the full dashboard payload (aggregates + per-topic progress).
 *
 * The dashboard endpoint is preferred over two separate calls
 * (`/topics` + manual aggregation) because it guarantees a consistent
 * snapshot — both numbers come from the same database read.
 */
export async function fetchDashboard(): Promise<DashboardStats> {
  const response = await fetch(
    `${API_BASE_URL}/api/progress/dashboard`,
    { headers: { ...getAuthHeaders() } },
  )

  if (!response.ok) {
    const err: ErrorResponse = await response.json()
    throw new Error(err.message)
  }

  return response.json()
}