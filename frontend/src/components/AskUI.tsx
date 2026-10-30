/**
 * Presentational components shared by the two tabs of the recruiter chatbot ("Ask Himmel"), the
 * chat and the fit check. They only show what the hooks give them.
 */
import type { ReactNode } from 'react'
import { useLang } from '../lang'
import type { AskError } from '../api'
import type { AskStep, AskToolCall } from '../schemas'
import { CONTACT } from '../contact.ts'
import { evalErrorBoxCls, secondaryButtonCls, textLinkCls } from '../styles.ts'

/**
 * A tool call as one line of code, each argument as `name: value`, e.g.
 * `get_entry(id: projects/finnish-learning-app)` or `list_entries(type: experience)`.
 */
function formatToolCall(tool: string, args: Record<string, unknown>): string {
  const parts = Object.entries(args).map(
    ([name, value]) => `${name}: ${typeof value === 'string' ? value : JSON.stringify(value)}`,
  )
  return `${tool}(${parts.join(', ')})`
}

/** A bullet line: optional indent, `-`, one space. */
const BULLET = /^\s*- /

/** A run of lines of one kind: a paragraph's lines, or a list's items. */
interface TextGroup {
  bullets: boolean
  lines: readonly string[]
}

/**
 * Splits an answer into paragraphs (on blank lines), and each paragraph into runs of plain lines
 * and runs of bullet lines, the `- ` removed.
 */
function groupsOf(text: string): readonly TextGroup[] {
  return text
    .split(/\n\s*\n/)
    .map((block) => block.trim())
    .filter((block) => block !== '')
    .flatMap((block) =>
      block.split('\n').reduce<readonly TextGroup[]>((groups, line) => {
        const bullets = BULLET.test(line)
        const content = bullets ? line.replace(BULLET, '') : line
        const last = groups.at(-1)
        return last?.bullets === bullets
          ? [...groups.slice(0, -1), { bullets, lines: [...last.lines, content] }]
          : [...groups, { bullets, lines: [content] }]
      }, []),
    )
}

/**
 * A chatbot answer as text. The model is told to write plain text: short paragraphs separated by
 * a blank line, and `- ` bullets. A paragraph becomes `<p>` (single line breaks kept), and a run
 * of bullet lines becomes `<ul>`.
 *
 * Everything is rendered as text nodes, never as HTML, so an answer that contains markup (or an
 * attack that made the model write some) shows the characters and runs nothing.
 */
export function MessageText({ text }: { text: string }) {
  return (
    <div className="flex flex-col gap-2">
      {groupsOf(text).map((group, i) =>
        group.bullets ? (
          <ul key={i} className="m-0 pl-5 list-disc flex flex-col gap-1">
            {group.lines.map((line, lineIndex) => (
              <li key={lineIndex}>{line}</li>
            ))}
          </ul>
        ) : (
          <p key={i} className="m-0 whitespace-pre-line">
            {group.lines.join('\n')}
          </p>
        ),
      )}
    </div>
  )
}

/**
 * The live progress of an answer: one row per step the backend reported, the newest last.
 * Earlier steps are done (✓); the newest one is still running (pulsing dot).
 *
 * `aria-live="polite"` makes a screen reader announce each new step when it is idle.
 */
export function ProgressSteps({ steps }: { steps: readonly AskStep[] }) {
  const { L } = useLang()

  const labelOf = (step: AskStep): ReactNode => {
    switch (step.kind) {
      case 'GUARDRAIL':
        return L.askStepGuardrail
      case 'TOOL_CALL':
        return (
          <>
            {L.askStepTool}{' '}
            <code className="font-mono text-[0.8rem] text-accent break-all">
              {formatToolCall(step.tool, step.args)}
            </code>
          </>
        )
      case 'COMPOSING':
        return L.askStepComposing
      case 'VERIFYING':
        return L.askStepVerifying
    }
  }

  return (
    <ol aria-live="polite" className="m-0 p-0 list-none flex flex-col gap-1.5">
      {steps.map((step, i) => {
        const running = i === steps.length - 1
        return (
          <li
            key={i}
            className={`flex items-start gap-2 text-[0.85rem] font-semibold ${
              running ? 'text-text-primary' : 'text-text-muted'
            }`}
          >
            {running ? (
              <span
                aria-hidden="true"
                className="mt-[7px] w-2 h-2 shrink-0 rounded-full bg-amber animate-pulse"
              />
            ) : (
              <span aria-hidden="true" className="w-2 shrink-0 text-green">
                ✓
              </span>
            )}
            <span className="min-w-0">{labelOf(step)}</span>
          </li>
        )
      })}
    </ol>
  )
}

/**
 * "How I found this": the profile tools the model used for an answer, as a collapsible list.
 * A call that failed (e.g. an unknown entry id) is marked.
 *
 * @param defaultOpen open when first shown; the chat opens it on the newest answer only
 */
export function ToolTrace({
  calls,
  defaultOpen = false,
}: {
  calls: readonly AskToolCall[]
  defaultOpen?: boolean
}) {
  const { L } = useLang()

  if (calls.length === 0) {
    return <p className="m-0 text-[0.75rem] text-text-muted font-semibold">{L.askNoTools}</p>
  }
  return (
    <details open={defaultOpen} className="text-[0.78rem]">
      <summary className="cursor-pointer select-none font-bold text-text-muted">
        {L.askHowIFound(calls.length)}
      </summary>
      <ol className="mt-1.5 m-0 pl-5 flex flex-col gap-1">
        {calls.map((call, i) => (
          <li key={i} className="text-text-sub">
            <code className="font-mono break-all">{formatToolCall(call.tool, call.args)}</code>
            {call.isError && <span className="ml-1.5 font-bold text-red">✕ {L.askToolError}</span>}
          </li>
        ))}
      </ol>
    </details>
  )
}

/** Long's email and LinkedIn, for the notices that send a recruiter elsewhere. */
export function ContactLinks() {
  const { L } = useLang()
  return (
    <p className="m-0 text-[0.85rem] text-text-sub font-semibold">
      {L.askContactIntro}{' '}
      <a href={`mailto:${CONTACT.email}`} className={textLinkCls}>
        {CONTACT.email}
      </a>
      {' · '}
      <a
        href={CONTACT.linkedInUrl}
        target="_blank"
        rel="noopener noreferrer"
        className={textLinkCls}
      >
        LinkedIn
      </a>
    </p>
  )
}

/**
 * Why a chatbot call failed, in the visitor's language. The backend's own `message` is English
 * only, so the text is chosen by `code`, the same way the essay feature's error view works.
 *
 * - `CAPPED`: the spending cap is full ("resting until tomorrow"), with contact links;
 * - `RATE_LIMITED`, `CONVERSATION_LIMIT`: a limit, with contact links; for the conversation
 *   limit also "Start a new conversation" when `onNewConversation` is given;
 * - `BLOCKED`: a neutral sentence; the guardrail's reasons are never shown;
 * - everything else: a "try again" text.
 *
 * `SESSION_EXPIRED` and `TURNSTILE_FAILED` never reach this view: the page handles them.
 */
export function AskErrorView({
  error,
  onNewConversation,
}: {
  error: AskError
  onNewConversation?: () => void
}) {
  const { L } = useLang()

  let message: string
  let showContact = false
  switch (error.code) {
    case 'BLOCKED':
      message = L.askErrBlocked
      break
    case 'CAPPED':
      message = error.capScope === 'MONTHLY' ? L.askErrCappedMonthly : L.askErrCappedDaily
      showContact = true
      break
    case 'RATE_LIMITED':
      message = L.askErrRateLimited
      showContact = true
      break
    case 'CONVERSATION_LIMIT':
      message = L.askErrConversationLimit
      showContact = true
      break
    case 'BUSY':
      message = L.askErrBusy
      break
    case 'UPSTREAM':
      message = L.askErrUpstream
      break
    case 'INTERRUPTED':
      message = L.askErrInterrupted
      break
    case 'BAD_REQUEST':
      message = L.askErrBadRequest
      break
    default:
      message = L.askErrGeneric
  }

  return (
    <div className="flex flex-col gap-2">
      <div role="alert" className={evalErrorBoxCls}>
        {message}
      </div>
      {showContact && <ContactLinks />}
      {error.code === 'CONVERSATION_LIMIT' && onNewConversation && (
        <button
          type="button"
          onClick={onNewConversation}
          className={`${secondaryButtonCls} self-start`}
        >
          {L.askNewConversation}
        </button>
      )}
    </div>
  )
}
