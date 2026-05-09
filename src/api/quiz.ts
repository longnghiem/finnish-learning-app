import {API_BASE_URL, getAuthHeaders} from "./config.ts";
import type {ErrorResponse} from "../types";

/**
 * A card returned by `GET /api/quiz/topics/{topicId}/cards`.
 *
 * Mirrors the backend `QuizCardResponse`. Fields:
 *
 * - `isNew` — true when the user has never reviewed this card - to render a "New" badge.
 * - `repetition` / `nextReviewDate` — null when `isNew` is true,
 *   otherwise reflect the persisted SM-2 state.
 */
export interface QuizCard {
  cardId: number
  topicId: number
  name: string
  exampleSentence: string
  translation: string
  imageUrl: string
  isNew: boolean
  repetition: number | null
  nextReviewDate: string | null
}

/**
 * Server response after submitting a quiz answer.
 *
 * Mirrors the backend `SubmitAnswerResponse`. The frontend uses this
 * primarily to update local progress state — the SM-2 schedule is
 * already persisted server-side by the time this response arrives.
 *
 * `correct` is the same `quality >= 3` rule the backend uses; it's
 * surfaced so the UI doesn't have to re-derive it.
 */
export interface SubmitAnswerResponse {
  cardId: number
  repetition: number
  easeFactor: number
  intervalDays: number
  nextReviewDate: string
  correct: boolean
}

/**
 * Fetches the next batch of cards to review for the authenticated
 * user in the given topic.
 */
export async function fetchQuizCards(
  topicId: number,
  limit: number = 10,
): Promise<QuizCard[]> {
  const url = new URL(`${API_BASE_URL}/api/quiz/topics/${topicId}/cards`)
  url.searchParams.set('limit', String(limit))

  const response = await fetch(
    url.toString(),
    { headers: { ...getAuthHeaders() }}
  )

  if (!response.ok) {
    const err: ErrorResponse = await response.json()
    throw new Error(err.message)
  }

  return response.json()
}

/**
 * Submits a self-graded quiz answer.
 */
export async function submitAnswer(
  cardId: number,
  quality: number,
): Promise<SubmitAnswerResponse> {
  const url = `${API_BASE_URL}/api/quiz/answer`

  const response = await fetch(
    url,
    {
      method: 'POST',
      headers: {'Content-Type': 'application/json', ...getAuthHeaders()},
      body: JSON.stringify({cardId, quality}),
    }
  )

  if (!response.ok) {
    const err: ErrorResponse = await response.json()
    throw new Error(err.message)
  }

  return response.json()
}