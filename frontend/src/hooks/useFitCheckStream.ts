import { useEffect, useState } from 'react'
import { type AskError, streamFitCheck } from '../api'
import { type StoredFitCheck, storedFitCheckSchema } from '../schemas'
import { useAskStream } from './useAskStream.ts'

/** The `sessionStorage` key of the last fit check. */
const STORAGE_KEY = 'ask_fit_check'

/** The stored fit check, or null if there is none or it is unreadable. */
function readStoredFitCheck(): StoredFitCheck | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (raw === null) return null
    const parsed = storedFitCheckSchema.safeParse(JSON.parse(raw))
    return parsed.success ? parsed.data : null
  } catch {
    console.warn('[useFitCheckStream] Stored fit check could not be read')
    return null
  }
}

function writeStoredFitCheck(checked: StoredFitCheck | null) {
  try {
    if (checked === null) sessionStorage.removeItem(STORAGE_KEY)
    else sessionStorage.setItem(STORAGE_KEY, JSON.stringify(checked))
  } catch {
    console.warn('[useFitCheckStream] Fit check could not be stored')
  }
}

/**
 * The "Check my fit" request: the progress steps while a job description is checked, then the
 * verified report.
 *
 * `check(jobDescription)` clears the previous report, streams the new one and keeps it in
 * `result` (the report with its turn id and tool trace), with the text it was made for in
 * `jobDescription`. It does nothing while a check is still running. A check can take up to 180 s;
 * if it fails, `status` is `error` and `error` says why.
 *
 * The last report is kept in `sessionStorage`, so a reload or a new chat session shows it again
 * and closing the tab forgets it. Unlike the chat, it isn't tied to a session: a report is about
 * the job description, not about a conversation.
 *
 * Call it in the page, not in the tab's panel: the panel unmounts while there is no session (e.g.
 * after "Start a new conversation"), and a running check must not be aborted then, because an
 * aborted check still counts toward the day's limit. A running check keeps the session id it
 * started with.
 *
 * @param sessionId the current session, or null before the Turnstile check: `check` then does nothing
 */
export function useFitCheckStream(sessionId: string | null) {
  const { status, error, currentSteps, run, isRunning } = useAskStream()
  const [checked, setChecked] = useState<StoredFitCheck | null>(readStoredFitCheck)

  useEffect(() => {
    writeStoredFitCheck(checked)
  }, [checked])

  /**
   * Checks one job description and streams its report.
   *
   * @returns how it ended: null when the report arrived, else the {@link AskError}, e.g.
   *   `SESSION_EXPIRED` for the page to ask for a new session
   */
  const check = async (jobDescription: string): Promise<AskError | null> => {
    if (sessionId === null || isRunning()) return null
    setChecked(null)
    const failure = await run(
      (onEvent, signal) => streamFitCheck(sessionId, jobDescription, onEvent, signal),
      (event) => {
        if (event.event === 'report') setChecked({ jobDescription, result: event.data })
      },
    )
    return failure ?? null
  }

  return {
    result: checked?.result ?? null,
    jobDescription: checked?.jobDescription ?? '',
    currentSteps,
    status,
    error,
    check,
  }
}

/** What {@link useFitCheckStream} returns. */
export type FitCheckStream = ReturnType<typeof useFitCheckStream>
