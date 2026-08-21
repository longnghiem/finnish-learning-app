import { API_BASE_URL, getAuthHeaders } from './config.ts'
import type { ErrorResponse } from '../types'

// ----------------------------------------------
// --  For both sentence and essay evaluations --
// ----------------------------------------------
export type FinnishLevel = 'A1.1' | 'A1.2' | 'A2.1' | 'A2.2' | 'B1.1' | 'B1.2'

export type EvaluationErrorCode = 'QUOTA' | 'UPSTREAM' | 'UNAUTHORIZED' | 'BAD_REQUEST' | 'GENERIC'

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

async function handleResponse<T>(response: Response): Promise<T> {
  if (response.ok) {
    try {
      return (await response.json()) as T
    } catch {
      // 2xx whose body isn't JSON
      throw new EvaluationError('UPSTREAM', response.statusText)
    }
  }

  let message: string
  try {
    const err = (await response.json()) as ErrorResponse
    message = err.message
  } catch {
    message = response.statusText
  }
  throw new EvaluationError(statusToCode(response.status), message)
}

// -----------------------------
// -- For sentence evaluation --
// -----------------------------
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

export async function evaluateSentence(
  sentence: string,
  word: string,
  meaning: string,
): Promise<EvaluateSentenceResponse> {
  const response = await fetch(`${API_BASE_URL}/api/evaluate-sentence`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
    body: JSON.stringify({ sentence, word, meaning }),
  })

  return handleResponse<EvaluateSentenceResponse>(response)
}

// --------------------------
// -- For essay evaluation --
// ---------------------------
/** Category of an {@link EssayIssue}. Mirrors the Kotlin `EssayIssueKind` enum. */
export type EssayIssueKind = 'GRAMMAR' | 'TYPO'

/**
 * One selectable essay prompt, as listed in the essay page dropdown.
 * Mirrors the backend `EssayPromptResponse`.
 */
export interface EssayPrompt {
  id: number
  title: string
}

/**
 * One concrete problem the model found in the essay.
 * Mirrors the backend `EssayIssue`.
 */
export interface EssayIssue {
  kind: EssayIssueKind
  original: string
  suggestion: string
}

/**
 * Essay evaluation result. Mirrors the backend `EvaluateEssayResponse`.
 */
export interface EvaluateEssayResponse {
  cefrLevel: FinnishLevel
  onTopic: boolean
  issues: EssayIssue[]
  feedback: string | null
}

export const MIN_ESSAY_LENGTH = 300
export const MAX_ESSAY_LENGTH = 2_500

/**
 * Lists the predefined essay prompts offered for one topic.
 */
export async function fetchEssayPrompts(topicId: number): Promise<EssayPrompt[]> {
  const response = await fetch(
    `${API_BASE_URL}/api/essays/prompts?topicId=${topicId}`,
    { headers: { ...getAuthHeaders() } },
  )

  return handleResponse<EssayPrompt[]>(response)
}

/**
 * Submits an essay for AI evaluation.
 *
 * Consumes one unit of the caller's daily quota and one billable Bedrock call, so callers
 * must not retry automatically — see `useEssayEvaluation`.
 *
 */
export async function evaluateEssay(
  promptId: number,
  essay: string,
): Promise<EvaluateEssayResponse> {
  const response = await fetch(`${API_BASE_URL}/api/essays/evaluate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
    body: JSON.stringify({ promptId, essay }),
  })

  return handleResponse<EvaluateEssayResponse>(response)
}