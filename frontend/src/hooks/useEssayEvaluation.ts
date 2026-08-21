import { useMutation } from '@tanstack/react-query'
import { evaluateEssay, type EvaluateEssayResponse, EvaluationError } from '../api/evaluation.ts'

interface EvaluateEssayVars {
  promptId: number
  essay: string
}

/**
 * React Query mutation for submitting an essay (`POST /api/essays/evaluate`).
 */
export function useEssayEvaluation() {
  return useMutation<EvaluateEssayResponse, EvaluationError, EvaluateEssayVars>({
    mutationFn: ({ promptId, essay }) => evaluateEssay(promptId, essay),
    retry: 0,
  })
}
