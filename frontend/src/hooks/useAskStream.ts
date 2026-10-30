import { useEffect, useRef, useState } from 'react'
import { AskError } from '../api'
import type { AskStep, AskStreamEvent } from '../schemas'

/** Where a stream stands: nothing running, waiting for the answer, or ended with an error. */
export type AskStreamStatus = 'idle' | 'streaming' | 'error'

/**
 * Starts one stream: a call of `streamChat` or `streamFitCheck` with everything but the last two
 * arguments already filled in.
 */
type StartStream = (onEvent: (event: AskStreamEvent) => void, signal: AbortSignal) => Promise<void>

/**
 * What the chat and the fit-check hooks share: one stream at a time, its progress steps, its
 * status and its error, and stopping it when the component goes away.
 *
 * Rules it keeps:
 * - every value the component shows is `useState`; only the `AbortController` is a `useRef`,
 *   because it is a handle to the running request, never shown;
 * - no state is set inside `useEffect`: the effect only returns the cleanup that aborts a running
 *   stream when the component unmounts;
 * - an abort is not an error: the stream was stopped on purpose.
 *
 * Internal to the hooks folder: components use {@link useChatStream} or
 * {@link useFitCheckStream}.
 */
export function useAskStream() {
  const [status, setStatus] = useState<AskStreamStatus>('idle')
  const [error, setError] = useState<AskError | null>(null)
  const [currentSteps, setCurrentSteps] = useState<readonly AskStep[]>([])
  const controllerRef = useRef<AbortController | null>(null)

  useEffect(() => () => controllerRef.current?.abort(), [])

  /**
   * True while a stream runs. For event handlers: unlike `status`, it is already true for a
   * second click that comes before the next render.
   */
  const isRunning = () => controllerRef.current !== null

  /**
   * Runs one stream, unless one is already running. Steps are collected in `currentSteps` until
   * the stream ends; every other event goes to `onEvent`. An `error` event or a thrown
   * {@link AskError} ends with `status` `error`.
   *
   * @returns how the stream ended: `null` with its result, the {@link AskError} without one, or
   *   `undefined` if it didn't run to the end (another stream was running, or it was aborted)
   */
  const run = async (
    start: StartStream,
    onEvent: (event: AskStreamEvent) => void,
  ): Promise<AskError | null | undefined> => {
    if (controllerRef.current !== null) return undefined
    const controller = new AbortController()
    controllerRef.current = controller
    setStatus('streaming')
    setError(null)
    setCurrentSteps([])

    let failure: AskError | null = null
    try {
      await start((event) => {
        if (event.event === 'step') setCurrentSteps((steps) => [...steps, event.data])
        else if (event.event === 'error') failure = AskError.fromStreamError(event.data)
        else onEvent(event)
      }, controller.signal)
    } catch (thrown) {
      if (controller.signal.aborted) return undefined
      failure = thrown instanceof AskError ? thrown : AskError.generic()
      if (!(thrown instanceof AskError))
        console.error('[useAskStream] Unexpected stream failure', {
          name: thrown instanceof Error ? thrown.name : typeof thrown,
        })
    } finally {
      controllerRef.current = null
    }
    setCurrentSteps([])
    setError(failure)
    setStatus(failure === null ? 'idle' : 'error')
    return failure
  }

  return { status, error, currentSteps, run, isRunning }
}
