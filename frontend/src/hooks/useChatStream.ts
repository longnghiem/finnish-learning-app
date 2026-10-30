import { useState } from 'react'
import { streamChat } from '../api'
import type { AskToolCall } from '../schemas'
import { useAskStream } from './useAskStream.ts'

/**
 * One message of the conversation, as the chat shows it. An assistant message carries the tools
 * the model used, for "How I found this".
 */
export type ChatMessage =
  | { id: string; role: 'user'; text: string }
  | {
      id: string
      role: 'assistant'
      text: string
      turnId: number
      toolTrace: readonly AskToolCall[]
    }

/**
 * The chat with the recruiter chatbot: the conversation so far, the progress steps of the answer
 * being made, and `send` for the next message.
 *
 * `send(text)` adds the visitor's message at once, streams the answer and adds it with its tool
 * trace when it arrives. It does nothing while an answer is still streaming. If the answer fails,
 * `status` is `error` and `error` says why (e.g. `BLOCKED`, `CAPPED`, `SESSION_EXPIRED`); the
 * visitor's message stays in the list, so they see what wasn't answered.
 *
 * The conversation lives in this hook's state only; the backend rebuilds the history from its own
 * records, never from the browser.
 *
 * @param sessionId the current session, or null before the Turnstile check: `send` then does nothing
 */
export function useChatStream(sessionId: string | null) {
  const { status, error, currentSteps, run, isRunning } = useAskStream()
  const [messages, setMessages] = useState<readonly ChatMessage[]>([])

  const send = async (text: string) => {
    if (sessionId === null || isRunning()) return
    setMessages((current) => [...current, { id: crypto.randomUUID(), role: 'user', text }])
    await run(
      (onEvent, signal) => streamChat(sessionId, text, onEvent, signal),
      (event) => {
        if (event.event !== 'answer') return
        const { turnId, text: answer, toolTrace } = event.data
        setMessages((current) => [
          ...current,
          { id: crypto.randomUUID(), role: 'assistant', text: answer, turnId, toolTrace },
        ])
      },
    )
  }

  return { messages, currentSteps, status, error, send }
}
