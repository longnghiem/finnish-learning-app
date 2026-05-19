import { useMutation } from '@tanstack/react-query'
import { evaluateSentence, type EvaluateSentenceResponse, EvaluationError } from '../api'

interface EvaluateVars {
  sentence: string
  word: string
  meaning: string
}

export function useSentenceEvaluation() {
  return useMutation<EvaluateSentenceResponse, EvaluationError, EvaluateVars>({
    mutationFn: ({ sentence, word, meaning }) => evaluateSentence(sentence, word, meaning),
    retry: 0,
  })
}