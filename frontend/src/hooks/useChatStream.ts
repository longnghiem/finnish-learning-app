import { useEffect, useState } from 'react'
import { type AskError, streamChat } from '../api'
import { type ChatMessage, type ChatMessageStatus, storedChatSchema } from '../schemas'
import { useAskStream } from './useAskStream.ts'

/** The `sessionStorage` key of the conversation. */
const STORAGE_KEY = 'ask_chat'

/**
 * The stored conversation of `sessionId`, or an empty one if there is none, it belongs to another
 * session, or it is unreadable. A message still `sending` when the page was left lost its answer,
 * so it comes back as `failed`.
 */
function readStoredChat(sessionId: string | null): readonly ChatMessage[] {
  if (sessionId === null) return []
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (raw === null) return []
    const parsed = storedChatSchema.safeParse(JSON.parse(raw))
    if (!parsed.success || parsed.data.sessionId !== sessionId) return []
    return parsed.data.messages.map((message) =>
      message.role === 'user' && message.status === 'sending'
        ? { ...message, status: 'failed' }
        : message,
    )
  } catch {
    console.warn('[useChatStream] Stored chat could not be read')
    return []
  }
}

function writeStoredChat(sessionId: string, messages: readonly ChatMessage[]) {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ sessionId, messages }))
  } catch {
    console.warn('[useChatStream] Chat could not be stored')
  }
}

/** The status a visitor's message gets from how its stream ended. */
function statusOf(failure: AskError | null): ChatMessageStatus {
  if (failure === null) return 'answered'
  return failure.code === 'BLOCKED' ? 'blocked' : 'failed'
}

/**
 * The chat with the recruiter chatbot: the conversation so far, the progress steps of the answer
 * being made, and `send` for the next message.
 *
 * `send(text)` adds the visitor's message at once as `sending`, streams the answer and adds it
 * with its tool trace when it arrives. Then the visitor's message becomes `answered`, `blocked`
 * or `failed`, so the page can count what the backend counts and mark what wasn't answered.
 * `send` does nothing while an answer is still streaming. If the answer fails, `status` is
 * `error` and `error` says why (e.g. `BLOCKED`, `CAPPED`, `SESSION_EXPIRED`).
 *
 * The conversation is kept in `sessionStorage` with its session id, so a reload shows it again and
 * closing the tab forgets it. Give the component that uses this hook `key={sessionId}`: a new
 * session then starts with an empty conversation. The backend rebuilds the model's history from
 * its own records, never from the browser.
 *
 * @param sessionId the current session, or null before the Turnstile check: `send` then does nothing
 */
export function useChatStream(sessionId: string | null) {
  const { status, error, currentSteps, run, isRunning } = useAskStream()
  const [messages, setMessages] = useState<readonly ChatMessage[]>(() => readStoredChat(sessionId))

  useEffect(() => {
    if (sessionId !== null) writeStoredChat(sessionId, messages)
  }, [sessionId, messages])

  /**
   * Sends one message and streams its answer.
   *
   * @returns how it ended: null when answered, else the {@link AskError}, e.g. `SESSION_EXPIRED`
   *   for the page to ask for a new session
   */
  const send = async (text: string): Promise<AskError | null> => {
    if (sessionId === null || isRunning()) return null
    const userMessageId = crypto.randomUUID()
    setMessages((current) => [
      ...current,
      { id: userMessageId, role: 'user', text, status: 'sending' },
    ])
    const failure = await run(
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
    if (failure === undefined) return null
    const messageStatus = statusOf(failure)
    setMessages((current) =>
      current.map((message) =>
        message.id === userMessageId && message.role === 'user'
          ? { ...message, status: messageStatus }
          : message,
      ),
    )
    return failure
  }

  return { messages, currentSteps, status, error, send }
}
