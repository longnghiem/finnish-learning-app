import {useMutation, useQuery} from "@tanstack/react-query";
import {fetchQuizCards, type QuizCard, submitAnswer, type SubmitAnswerResponse} from "../api/quiz.ts";

const QUIZ_QUERY_KEY = ['quiz'] as const

/**
 * React Query hook for the quiz card batch.
 */
export function useQuizCards(
  topicId: number,
  enabled: boolean = true
) {
  return useQuery<QuizCard[], Error>({
    queryKey: [...QUIZ_QUERY_KEY, 'cards', topicId],
    queryFn: () => fetchQuizCards(topicId),
    staleTime: 0, // This is the default, but we want to be explicit that quiz cards should always be refetched when the component mounts
    enabled,
  })
}

/**
 * Mutation hook for submitting one quiz answer.
 *
 * Intentionally does **not** invalidate `QUIZ_QUERY_KEY` on success.
 * The quiz page maintains a local card queue and progresses through
 * it client-side; refetching mid-session would replace the in-flight
 * batch and disrupt the flow. A fresh batch is only fetched on the
 * next quiz session (handled by `useQuizCards` + `staleTime: 0`).
 */
export function useSubmitAnswer() {
  return useMutation<SubmitAnswerResponse, Error, {cardId: number, quality: number}>({
    mutationFn: ({cardId, quality}) => submitAnswer(cardId, quality),
  })
}