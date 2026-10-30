import { useState } from 'react'
import { streamFitCheck } from '../api'
import type { AskReport } from '../schemas'
import { useAskStream } from './useAskStream.ts'

/**
 * The "Check my fit" request: the progress steps while a job description is checked, then the
 * verified report.
 *
 * `check(jobDescription)` clears the previous report, streams the new one and keeps it in
 * `result` (the report with its turn id and tool trace). It does nothing while a check is still
 * running. A check can take up to 180 s; if it fails, `status` is `error` and `error` says why.
 *
 * @param sessionId the current session, or null before the Turnstile check: `check` then does nothing
 */
export function useFitCheckStream(sessionId: string | null) {
  const { status, error, currentSteps, run, isRunning } = useAskStream()
  const [result, setResult] = useState<AskReport | null>(null)

  const check = async (jobDescription: string) => {
    if (sessionId === null || isRunning()) return
    setResult(null)
    await run(
      (onEvent, signal) => streamFitCheck(sessionId, jobDescription, onEvent, signal),
      (event) => {
        if (event.event === 'report') setResult(event.data)
      },
    )
  }

  return { result, currentSteps, status, error, check }
}
