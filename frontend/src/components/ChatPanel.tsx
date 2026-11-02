import { type KeyboardEvent, useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import { useLang } from '../lang'
import type { ChatStream } from '../hooks'
import { AskError, MAX_CHAT_MESSAGE_LENGTH } from '../api'
import type { AskSession, ChatMessage } from '../schemas'
import {
  evalPanelCls,
  evalPanelIntroCls,
  evalTextareaCls,
  fieldHintCls,
  finePrintCls,
  textLinkCls,
} from '../styles.ts'
import { EvalSubmitButton } from './EvaluationUI.tsx'
import { AskErrorView, MessageText, ProgressSteps, ToolTrace } from './AskUI.tsx'

/**
 * The limit notice once the counter reaches 0. The backend answers `CONVERSATION_LIMIT` only to
 * one more message, and the panel never sends one, so it shows the notice itself.
 */
const CONVERSATION_FULL = new AskError(
  'CONVERSATION_LIMIT',
  'This conversation has used all its messages.',
)

interface ChatPanelProps {
  /** The current session: the panel shows how many of its messages are left. */
  session: AskSession
  /**
   * The conversation and its stream ({@link ChatStream}), made by the page, which also shows
   * Himmel's state from it.
   */
  chat: ChatStream
  /** The message being typed; kept in the page, which shows Himmel listening while it has text. */
  draft: string
  onDraftChange: (draft: string) => void
  /** Called when the backend says the session has expired: the page asks for a new one. */
  onSessionExpired: () => void
  /** Called for "Start a new conversation" after the conversation limit. */
  onNewConversation: () => void
  /** False while another tab is shown: the panel stays mounted, so an answer keeps streaming. */
  active: boolean
}

/**
 * The Chat tab: the conversation, the live progress of the answer being made, and the input.
 *
 * - The first view shows starter questions; clicking one sends it.
 * - Enter sends, Shift+Enter adds a line. While an answer streams, the input stays editable but
 *   sending is off, so a visitor can write the next question.
 * - "x of 10 messages left" counts what the backend counts: messages that were answered,
 *   declined by the guardrail, or are still being answered. The backend has the last word with
 *   `CONVERSATION_LIMIT`.
 * - At 0 left, or on `CONVERSATION_LIMIT`, the input switches off and {@link AskErrorView}
 *   shows the limit notice, the contact links and "Start a new conversation".
 * - A message that wasn't answered is marked. The newest of the visitor's messages also gets
 *   "Try again": an older one was followed by other questions, so resending it would be out of
 *   place.
 * - The newest answer shows its "How I found this" list open.
 */
export function ChatPanel({
  session,
  chat,
  draft,
  onDraftChange,
  onSessionExpired,
  onNewConversation,
  active,
}: ChatPanelProps) {
  const { L } = useLang()
  const listRef = useRef<HTMLDivElement>(null)

  // Keeps the newest message and step in view: the list is a scroll box of its own, so the page
  // itself doesn't jump. A hidden box can't scroll and loses its position, so this runs again
  // when the tab is shown.
  useEffect(() => {
    const list = listRef.current
    if (active && list) list.scrollTop = list.scrollHeight
  }, [chat.messages, chat.currentSteps, active])

  const used = chat.messages.filter(
    (message) => message.role === 'user' && message.status !== 'failed',
  ).length
  const remaining = Math.max(session.maxMessages - used, 0)
  const limitReached = remaining === 0 || chat.error?.code === 'CONVERSATION_LIMIT'
  const streaming = chat.status === 'streaming'
  const canSend = !streaming && !limitReached
  const latestAnswerId = chat.messages.findLast((message) => message.role === 'assistant')?.id
  const latestQuestionId = chat.messages.findLast((message) => message.role === 'user')?.id

  const send = async (text: string) => {
    const trimmed = text.trim()
    if (!canSend || trimmed === '') return
    onDraftChange('')
    const failure = await chat.send(trimmed)
    if (failure?.code === 'SESSION_EXPIRED') onSessionExpired()
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    // isComposing: Enter that confirms an input-method candidate (e.g. Japanese) isn't a send.
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault()
      void send(draft)
    }
  }

  return (
    <section aria-label={L.askTabChat} className={evalPanelCls}>
      <div
        ref={listRef}
        role="log"
        aria-live="polite"
        className="flex flex-col gap-3 max-h-[min(60vh,560px)] overflow-y-auto pr-1"
      >
        {chat.messages.length === 0 && (
          <StarterQuestions disabled={!canSend} onPick={(question) => void send(question)} />
        )}
        {chat.messages.map((message) => (
          <MessageBubble
            key={message.id}
            message={message}
            latest={message.id === latestAnswerId}
            canRetry={canSend && message.id === latestQuestionId}
            onRetry={(text) => void send(text)}
          />
        ))}
        {streaming && chat.currentSteps.length > 0 && <ProgressSteps steps={chat.currentSteps} />}
      </div>

      {chat.error &&
        chat.error.code !== 'SESSION_EXPIRED' &&
        chat.error.code !== 'CONVERSATION_LIMIT' && <AskErrorView error={chat.error} />}
      {limitReached && (
        <AskErrorView error={CONVERSATION_FULL} onNewConversation={onNewConversation} />
      )}

      <div className="flex flex-col">
        <label htmlFor="ask-chat-input" className="sr-only">
          {L.askInputLabel}
        </label>
        <textarea
          id="ask-chat-input"
          rows={2}
          value={draft}
          maxLength={MAX_CHAT_MESSAGE_LENGTH}
          disabled={limitReached}
          onChange={(e) => onDraftChange(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={L.askPlaceholder}
          aria-describedby="ask-chat-hint"
          className={evalTextareaCls}
        />
        <div
          id="ask-chat-hint"
          className={`${fieldHintCls} mt-1.5 flex items-center justify-between gap-3 flex-wrap`}
        >
          <span>{L.askMessagesLeft(remaining, session.maxMessages)}</span>
          <span>
            {L.askEnterHint} · {L.askCharCounter(draft.length, MAX_CHAT_MESSAGE_LENGTH)}
          </span>
        </div>
      </div>

      <div className="flex items-center justify-between gap-3 flex-wrap">
        <p className={`${finePrintCls} flex-1 min-w-[200px]`}>
          {L.askNotice}{' '}
          <Link to="/privacy" className={textLinkCls}>
            {L.askPrivacyLink}
          </Link>
        </p>
        <EvalSubmitButton
          enabled={canSend && draft.trim() !== ''}
          pending={streaming}
          onClick={() => void send(draft)}
          label={L.askSend}
          pendingLabel={L.askSending}
        />
      </div>
    </section>
  )
}

/** The starter questions of an empty chat, as buttons. */
function StarterQuestions({
  disabled,
  onPick,
}: {
  disabled: boolean
  onPick: (question: string) => void
}) {
  const { L } = useLang()
  return (
    <div className="flex flex-col gap-2.5">
      <p className={evalPanelIntroCls}>{L.askEmptyHint}</p>
      <div className="flex flex-wrap gap-2">
        {L.askStarters.map((question) => (
          <button
            key={question}
            type="button"
            disabled={disabled}
            onClick={() => onPick(question)}
            className="rounded-full border border-border bg-surface-alt px-3.5 py-1.5 text-left
              text-[0.82rem] font-semibold text-text-primary cursor-pointer font-[inherit]
              transition-[border-color] duration-150 hover:border-amber
              disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {question}
          </button>
        ))}
      </div>
    </div>
  )
}

/**
 * One message. The visitor's own sit on the right; the chatbot's on the left, with their tool
 * trace. A visitor's message that wasn't answered says so.
 */
function MessageBubble({
  message,
  latest,
  canRetry,
  onRetry,
}: {
  message: ChatMessage
  latest: boolean
  canRetry: boolean
  onRetry: (text: string) => void
}) {
  const { L } = useLang()

  if (message.role === 'assistant') {
    return (
      <div
        className="self-start max-w-[92%] flex flex-col gap-2 rounded-2xl rounded-tl-sm border
          border-border bg-surface-alt px-4 py-3 text-[0.92rem] leading-[1.55] text-text-primary"
      >
        <span className="text-[0.7rem] font-extrabold uppercase tracking-[0.5px] text-amber">
          {L.askTitle}
        </span>
        <MessageText text={message.text} />
        <ToolTrace calls={message.toolTrace} defaultOpen={latest} />
      </div>
    )
  }

  return (
    <div className="self-end max-w-[85%] flex flex-col items-end gap-1">
      <div
        className="rounded-2xl rounded-tr-sm bg-accent text-white px-4 py-2.5 text-[0.92rem]
          leading-[1.55] whitespace-pre-wrap break-words"
      >
        <span className="sr-only">{L.askYou}: </span>
        {message.text}
      </div>
      {message.status === 'blocked' && (
        <span className="text-[0.72rem] font-bold text-text-muted">{L.askDeclined}</span>
      )}
      {message.status === 'failed' && (
        <span className="text-[0.72rem] font-bold text-red">
          {L.askNotAnswered}
          {canRetry && (
            <>
              {' · '}
              <button
                type="button"
                onClick={() => onRetry(message.text)}
                className={`bg-transparent border-none p-0 font-[inherit] cursor-pointer ${textLinkCls}`}
              >
                {L.askTryAgain}
              </button>
            </>
          )}
        </span>
      )}
    </div>
  )
}
