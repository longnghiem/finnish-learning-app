import { useMutation } from '@tanstack/react-query'
import { evaluateSentence, type EvaluateSentenceResponse, EvaluationError } from '../api'

export function useSentenceEvaluation() {
  return useMutation<EvaluateSentenceResponse, EvaluationError, string>({
    mutationFn: (sentence: string) => evaluateSentence(sentence),
    retry: 0,
  })
}