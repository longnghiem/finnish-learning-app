import { z } from 'zod'
import { API_BASE_URL, getAuthHeaders } from '../config.ts'
import {
  type AdminDailyStats,
  type AdminSession,
  type AdminTurnPage,
  type AskMode,
  type TurnStatus,
  adminDailyStatsSchema,
  adminSessionSchema,
  adminTurnPageSchema,
} from '../../schemas/admin/adminAsk.ts'

/*
 * Client of the chatbot's admin API (`/api/admin/ask`). Every call sends the admin's JWT; the
 * backend answers 401 without a valid token and 403 for a user who isn't an admin. The backend
 * side is `backend/.../controller/AdminAskController.kt`, the response shapes are in
 * `schemas/admin/adminAsk.ts`.
 */

/** Turns per page of the admin table. The backend allows 1 to 100. */
export const ADMIN_TURNS_PAGE_SIZE = 50

/**
 * The filters of the turn list; all optional and combined with AND. `from` and `to` are
 * Europe/Helsinki days as `YYYY-MM-DD` (what `<input type="date">` gives), both inclusive.
 */
export interface AdminTurnFilters {
  mode?: AskMode
  status?: TurnStatus
  from?: string
  to?: string
}

/**
 * A failed admin call. `message` is meant for the admin: the backend's own text where there is
 * one (e.g. "from must not be after to"), else a short explanation of the status.
 */
export class AdminAskError extends Error {
  /** The HTTP status, or 0 if the backend couldn't be reached. */
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'AdminAskError'
    this.status = status
  }
}

const errorBodySchema = z.object({ message: z.string() })

/** The text for a failed response: the backend's message, or one chosen by status. */
async function errorOf(response: Response): Promise<AdminAskError> {
  let body: unknown = null
  try {
    body = await response.json()
  } catch {
    // No JSON body, e.g. a 403 from Spring Security or an HTML page from a proxy.
  }
  const parsed = errorBodySchema.safeParse(body)
  const message =
    response.status === 401
      ? 'Your login has expired. Please log in again.'
      : response.status === 403
        ? 'Only an admin can see the chatbot data.'
        : parsed.success
          ? parsed.data.message
          : `The request failed (HTTP ${response.status}).`
  console.warn('[api/adminAsk] Request failed', { url: response.url, status: response.status })
  return new AdminAskError(response.status, message)
}

/** `fetch` with the admin's token; a network failure becomes an {@link AdminAskError} with status 0. */
async function send(path: string, init: RequestInit = {}): Promise<Response> {
  try {
    return await fetch(`${API_BASE_URL}${path}`, { ...init, headers: getAuthHeaders() })
  } catch {
    console.warn('[api/adminAsk] Backend unreachable', { path })
    throw new AdminAskError(0, "The backend can't be reached.")
  }
}

/**
 * One page of turns, newest first (`GET /api/admin/ask/turns`).
 *
 * @param offset how many newer turns to skip: the number of turns already loaded
 * @throws AdminAskError e.g. 400 when `from` is after `to`
 */
export async function fetchAdminTurns(
  filters: AdminTurnFilters,
  offset: number,
): Promise<AdminTurnPage> {
  const params = new URLSearchParams()
  if (filters.mode) params.set('mode', filters.mode)
  if (filters.status) params.set('status', filters.status)
  if (filters.from) params.set('from', filters.from)
  if (filters.to) params.set('to', filters.to)
  params.set('limit', String(ADMIN_TURNS_PAGE_SIZE))
  params.set('offset', String(offset))
  const response = await send(`/api/admin/ask/turns?${params}`)
  if (!response.ok) throw await errorOf(response)
  return adminTurnPageSchema.parse(await response.json())
}

/**
 * One session with all its turns, oldest first (`GET /api/admin/ask/sessions/{id}`).
 *
 * @throws AdminAskError 404 if the session doesn't exist (deleted, or purged after 30 days)
 */
export async function fetchAdminSession(sessionId: string): Promise<AdminSession> {
  const response = await send(`/api/admin/ask/sessions/${encodeURIComponent(sessionId)}`)
  if (!response.ok) throw await errorOf(response)
  return adminSessionSchema.parse(await response.json())
}

/**
 * Deletes a session and all its turns (`DELETE /api/admin/ask/sessions/{id}`). The daily totals
 * stay.
 *
 * @throws AdminAskError 404 if the session doesn't exist
 */
export async function deleteAdminSession(sessionId: string): Promise<void> {
  const response = await send(`/api/admin/ask/sessions/${encodeURIComponent(sessionId)}`, {
    method: 'DELETE',
  })
  if (!response.ok) throw await errorOf(response)
}

/**
 * The daily totals, oldest first (`GET /api/admin/ask/daily-stats`). Without dates, the 30 days
 * up to today. Days without activity are missing.
 */
export async function fetchAdminDailyStats(): Promise<AdminDailyStats[]> {
  const response = await send('/api/admin/ask/daily-stats')
  if (!response.ok) throw await errorOf(response)
  return z.array(adminDailyStatsSchema).parse(await response.json())
}
