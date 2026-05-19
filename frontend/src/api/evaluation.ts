import { API_BASE_URL, getAuthHeaders } from './config.ts'
import type { ErrorResponse } from '../types'

export type FinnishLevel = 'A1' | 'A2' | 'B1' | 'B2' | 'C1' | 'C2'

/**
 * Mirrors backend `EvaluateSentenceResponse`
 */
export interface EvaluateSentenceResponse {
  hasGrammarMistake: boolean
  hasTypo: boolean
  level: FinnishLevel
  correction: string | null
  b1Example: string | null
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