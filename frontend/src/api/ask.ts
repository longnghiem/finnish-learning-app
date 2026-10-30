/*
 * Client of the recruiter chatbot's API ("Ask Himmel", `/api/ask`). The endpoints are anonymous:
 * no auth header. The backend side is `backend/.../controller/AskController.kt`, and the shapes of
 * every response and event are in `schemas/ask.ts`.
 *
 * The chat and the fit check answer with Server-Sent Events (SSE): one POST, then a stream of
 * events that stays open until the answer is ready.
 *
 *   event:step     data:{"kind":"GUARDRAIL"}
 *   event:step     data:{"kind":"TOOL_CALL","tool":"get_entry","args":{"id":"projects/x"}}
 *   event:step     data:{"kind":"COMPOSING"}
 *   event:step     data:{"kind":"VERIFYING"}                     (fit check only)
 *   event:answer   data:{"turnId":123,"text":"…","toolTrace":[…]}
 *   event:report   data:{"turnId":124,"report":{…},"toolTrace":[…]}   (fit check only)
 *   event:error    data:{"code":"CAPPED","message":"…","capScope":"DAILY"}
 *   event:done     data:{}
 *   :keepalive                                                    (fit check only, every 15 s)
 *
 * A stream ends with its result (`answer` or `report`) or with `error`, then `done`. One that
 * ends without `done` timed out (55 s chat, 180 s fit check) or broke on the way.
 *
 * Problems are logged to the browser console, prefixed `[api/ask]`, with paths, status codes and
 * counts only: never what the visitor typed or what the chatbot answered.
 */
import { API_BASE_URL } from './config.ts'
import { SseParser } from './sse.ts'
import {
  type AskSession,
  type AskStats,
  type AskStreamError,
  type AskStreamEvent,
  askErrorBodySchema,
  askSessionSchema,
  askStatsSchema,
  askStreamEventSchema,
} from '../schemas/ask.ts'

/**
 * Why a chatbot call failed:
 * - from the backend, as an HTTP error or a stream `error` event: `SESSION_EXPIRED`,
 *   `TURNSTILE_FAILED`, `RATE_LIMITED`, `CONVERSATION_LIMIT`, `BUSY`, `BLOCKED`, `CAPPED`,
 *   `UPSTREAM`, `INTERNAL`;
 * - `BAD_REQUEST`: an HTTP 400, e.g. a message that is too long (it has no code of its own);
 * - `INTERRUPTED`: the stream ended without `done`, a timeout or a broken connection;
 * - `GENERIC`: anything else, e.g. the backend can't be reached.
 */
export type AskErrorCode =
  | 'SESSION_EXPIRED'
  | 'TURNSTILE_FAILED'
  | 'RATE_LIMITED'
  | 'CONVERSATION_LIMIT'
  | 'BUSY'
  | 'BLOCKED'
  | 'CAPPED'
  | 'UPSTREAM'
  | 'INTERNAL'
  | 'BAD_REQUEST'
  | 'INTERRUPTED'
  | 'GENERIC'

const BACKEND_CODES: readonly string[] = [
  'SESSION_EXPIRED',
  'TURNSTILE_FAILED',
  'RATE_LIMITED',
  'CONVERSATION_LIMIT',
  'BUSY',
  'BLOCKED',
  'CAPPED',
  'UPSTREAM',
  'INTERNAL',
]

/**
 * A failed chatbot call. `message` is safe to show: the backend writes it for the visitor, and the
 * frontend's own messages are generic.
 */
export class AskError extends Error {
  readonly code: AskErrorCode
  /** Which spending cap is full, only for `CAPPED`. */
  readonly capScope?: 'DAILY' | 'MONTHLY'

  constructor(code: AskErrorCode, message: string, capScope?: 'DAILY' | 'MONTHLY') {
    super(message)
    this.name = 'AskError'
    this.code = code
    this.capScope = capScope
  }

  /** The error when the frontend has no better text, e.g. the backend can't be reached. */
  static generic(): AskError {
    return new AskError('GENERIC', GENERIC_MESSAGE)
  }

  /** The error for a stream's `error` event. */
  static fromStreamError(error: AskStreamError): AskError {
    return new AskError(error.code, error.message, error.capScope)
  }
}

/** Shown when the frontend has no better text, e.g. the backend can't be reached. */
const GENERIC_MESSAGE = 'Something went wrong. Please try again.'

/** Shown when a stream ends without `done`. */
const INTERRUPTED_MESSAGE = 'The answer took too long or the connection broke. Please try again.'

/** The error for a non-2xx response: its `code` if it has a known one, else from the status. */
async function errorOf(response: Response): Promise<AskError> {
  let body: unknown = null
  try {
    body = await response.json()
  } catch {
    // Not JSON, e.g. an HTML error page from a proxy.
  }
  const parsed = askErrorBodySchema.safeParse(body)
  const message = parsed.success ? parsed.data.message : GENERIC_MESSAGE
  const backendCode = parsed.success ? parsed.data.code : undefined
  const code: AskErrorCode =
    backendCode !== undefined && BACKEND_CODES.includes(backendCode)
      ? (backendCode as AskErrorCode)
      : response.status === 400
        ? 'BAD_REQUEST'
        : 'GENERIC'
  console.warn('[api/ask] Request failed', { url: response.url, status: response.status, code })
  return new AskError(code, message)
}

/** `fetch`, with a network failure turned into a `GENERIC` {@link AskError}. Aborts stay aborts. */
async function send(path: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(`${API_BASE_URL}${path}`, init)
  } catch (error) {
    if (init.signal?.aborted) throw error
    console.warn('[api/ask] Backend unreachable', { path })
    throw AskError.generic()
  }
}

/**
 * Exchanges a Cloudflare Turnstile token for a chat session (`POST /api/ask/session`).
 *
 * A token can be used once: after any failure (`TURNSTILE_FAILED`, `RATE_LIMITED`, `UPSTREAM`),
 * reset the widget to get a new one.
 *
 * @throws AskError if the backend refuses
 */
export async function createAskSession(turnstileToken: string): Promise<AskSession> {
  const response = await send('/api/ask/session', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ turnstileToken }),
  })
  if (!response.ok) throw await errorOf(response)
  return askSessionSchema.parse(await response.json())
}

/** Fetches the public totals (`GET /api/ask/stats`). */
export async function fetchAskStats(): Promise<AskStats> {
  const response = await send('/api/ask/stats', {})
  if (!response.ok) throw await errorOf(response)
  return askStatsSchema.parse(await response.json())
}

/**
 * Sends one chat message (`POST /api/ask/chat`) and calls `onEvent` for every event of the answer
 * stream, in order. Resolves after `done`.
 *
 * @throws AskError for an HTTP error before the stream (e.g. `SESSION_EXPIRED`, `BUSY`), or
 *   `INTERRUPTED` if the stream ends without `done`. An `error` event is not thrown: it is passed
 *   to `onEvent` like any other event
 */
export function streamChat(
  sessionId: string,
  message: string,
  onEvent: (event: AskStreamEvent) => void,
  signal: AbortSignal,
): Promise<void> {
  return streamAsk('/api/ask/chat', { sessionId, message }, onEvent, signal)
}

/**
 * Checks a job description (`POST /api/ask/fit-check`) and calls `onEvent` for every event of the
 * report stream, in order. Resolves after `done`. Same errors as {@link streamChat}.
 */
export function streamFitCheck(
  sessionId: string,
  jobDescription: string,
  onEvent: (event: AskStreamEvent) => void,
  signal: AbortSignal,
): Promise<void> {
  return streamAsk('/api/ask/fit-check', { sessionId, jobDescription }, onEvent, signal)
}

/**
 * POSTs `body` and reads the SSE answer as it arrives.
 *
 * `EventSource`, the browser's built-in SSE client, can only send GET requests without a body, so
 * this reads the stream itself: `fetch`, then `response.body.getReader()`, chunk by chunk. A
 * `TextDecoder` in stream mode turns the bytes into text (a character may be split between two
 * chunks), and {@link SseParser} turns the text into messages.
 */
async function streamAsk(
  path: string,
  body: Record<string, string>,
  onEvent: (event: AskStreamEvent) => void,
  signal: AbortSignal,
): Promise<void> {
  const startedAt = Date.now()
  const response = await send(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify(body),
    signal,
  })
  if (!response.ok) throw await errorOf(response)
  if (response.body === null) throw new AskError('INTERRUPTED', INTERRUPTED_MESSAGE)

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  const parser = new SseParser()
  let events = 0
  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      for (const message of parser.push(decoder.decode(value, { stream: true }))) {
        const event = toEvent(path, message.event, message.data)
        events += 1
        onEvent(event)
        if (event.event === 'done') return
      }
    }
  } catch (error) {
    if (signal.aborted) throw error
    console.warn('[api/ask] Stream broke', { path, events })
    throw new AskError('INTERRUPTED', INTERRUPTED_MESSAGE)
  } finally {
    reader.releaseLock()
  }
  console.warn('[api/ask] Stream ended without done', {
    path,
    events,
    durationMs: Date.now() - startedAt,
  })
  throw new AskError('INTERRUPTED', INTERRUPTED_MESSAGE)
}

/** The checked event, or an `INTERNAL` error event if the message isn't a known event. */
function toEvent(path: string, name: string, data: string): AskStreamEvent {
  let json: unknown
  try {
    json = JSON.parse(data)
  } catch {
    console.error('[api/ask] Stream event is not JSON', { path, event: name })
    return internalError()
  }
  const parsed = askStreamEventSchema.safeParse({ event: name, data: json })
  if (parsed.success) return parsed.data
  console.error('[api/ask] Stream event has an unknown shape', {
    path,
    event: name,
    issues: parsed.error.issues.length,
  })
  return internalError()
}

function internalError(): AskStreamEvent {
  return { event: 'error', data: { code: 'INTERNAL', message: GENERIC_MESSAGE } }
}
