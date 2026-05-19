import { API_BASE_URL, getAuthHeaders } from './config.ts'
import type { ErrorResponse } from '../types'

export type FinnishLevel = 'A1.1' | 'A1.2' | 'A2.1' | 'A2.2' | 'B1.1' | 'B1.2'

/**
 * Mirrors backend `EvaluateSentenceResponse`. Field names match the JSON
 * the AI evaluator emits (see backend prompt file).
 */
export interface EvaluateSentenceResponse {
  hasTypo: boolean
  hasGrammarMistake: boolean
  wordUsedCorrectly: boolean
  cefrLevel: FinnishLevel
  feedback: string
  correction: string | null
}

export type EvaluationErrorCode =
  | 'QUOTA'
  | 'UPSTREAM'
  | 'UNAUTHORIZED'
  | 'BAD_REQUEST'
  | 'GENERIC'

export class EvaluationError extends Error {
  readonly code: EvaluationErrorCode

  constructor(code: EvaluationErrorCode, message: string) {
    super(message)
    this.name = 'EvaluationError'
    this.code = code
  }
}

function statusToCode(status: number): EvaluationErrorCode {
  switch (status) {
    case 400:
      return 'BAD_REQUEST'
    case 401:
      return 'UNAUTHORIZED'
    case 429:
      return 'QUOTA'
    case 502:
      return 'UPSTREAM'
    default:
      return 'GENERIC'
  }
}

export async function evaluateSentence(
  sentence: string,
): Promise<EvaluateSentenceResponse> {
  const response = await fetch(`${API_BASE_URL}/api/evaluate-sentence`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
    body: JSON.stringify({ sentence }),
  })

  if (response.ok) return response.json()

  let message: string
  try {
    const err = (await response.json()) as ErrorResponse
    message = err.message
  } catch {
    message = response.statusText
  }
  throw new EvaluationError(statusToCode(response.status), message)
}