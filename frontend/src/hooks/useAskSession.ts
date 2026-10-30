import { useState } from 'react'
import { createAskSession } from '../api'
import { type AskSession, askSessionSchema } from '../schemas'

/** The `sessionStorage` key of the current chat session. */
const STORAGE_KEY = 'ask_session'

/**
 * The stored session, or null if there is none, it is unreadable, or it has expired. Storage can
 * throw (e.g. when the browser blocks it), so every access is wrapped.
 */
function readStoredSession(): AskSession | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (raw === null) return null
    const parsed = askSessionSchema.safeParse(JSON.parse(raw))
    if (!parsed.success || Date.parse(parsed.data.expiresAt) <= Date.now()) {
      sessionStorage.removeItem(STORAGE_KEY)
      return null
    }
    return parsed.data
  } catch {
    console.warn('[useAskSession] Stored session could not be read')
    writeStoredSession(null)
    return null
  }
}

function writeStoredSession(session: AskSession | null) {
  try {
    if (session === null) sessionStorage.removeItem(STORAGE_KEY)
    else sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  } catch {
    console.warn('[useAskSession] Session could not be stored')
  }
}

/**
 * The visitor's chat session: created once after the Turnstile check, kept in `sessionStorage`
 * so a reload keeps the conversation going, gone when the tab closes.
 *
 * The stored session is read once, when the hook starts, and an expired one is dropped then. A
 * session that expires while the page is open is noticed by the backend: the next message gets
 * `SESSION_EXPIRED`, and the page calls `clear()` and shows the check again.
 *
 * @returns `session` (null until there is one), `create(token)` and `clear()`
 */
export function useAskSession() {
  const [session, setSession] = useState<AskSession | null>(readStoredSession)

  /**
   * Exchanges a Turnstile token for a session and stores it.
   *
   * @throws AskError if the backend refuses; reset the Turnstile widget then, the token is used up
   */
  const create = async (turnstileToken: string): Promise<AskSession> => {
    const created = await createAskSession(turnstileToken)
    writeStoredSession(created)
    setSession(created)
    return created
  }

  /** Forgets the session, e.g. after `SESSION_EXPIRED`. */
  const clear = () => {
    writeStoredSession(null)
    setSession(null)
  }

  return { session, create, clear }
}
