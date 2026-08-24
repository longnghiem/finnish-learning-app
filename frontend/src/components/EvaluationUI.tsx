/**
 * Presentational components shared by the two AI evaluation features:
 * `SentenceEvaluationPanel` (Groq, per-word sentences) and `EssayPage` (Bedrock, essays).
 */

import { useLang } from '../lang'
import type { EvaluationErrorCode, FinnishLevel } from '../api'
import { dashSectionLabelCls, evalErrorBoxCls } from '../styles.ts'

/** Small inline loading spinner, sized for a button. */
export function Spinner() {
  return (
    <span
      aria-hidden="true"
      className="inline-block w-3 h-3 rounded-full border-2 border-white/40 border-t-white animate-spin"
    />
  )
}

/** The amber "AI" badge that marks a panel as model-generated. */
export function AiBadge() {
  return (
    <span className="text-[10px] font-extrabold text-amber tracking-[0.8px] uppercase bg-amber/15 border border-amber/35 px-2 py-[3px] rounded-md">
      AI
    </span>
  )
}

/**
 * Small status pill. Green-ish when `ok`, red-ish otherwise.
 * Leading ✓ / ✕ glyph signals state at a glance.
 */
export function Chip({ ok, label }: { ok: boolean; label: string }) {
  const cls = ok
    ? 'bg-green/15 text-green border-green/40'
    : 'bg-red/15 text-red border-red/40'
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-[3px]
        text-[0.7rem] font-extrabold uppercase tracking-[0.3px] ${cls}`}
    >
      <span aria-hidden="true">{ok ? '✓' : '✕'}</span>
      {label}
    </span>
  )
}

/**
 * CEFR sub-level pill (e.g. "Level A2.1").
 *
 * @param className extra positioning classes — the sentence panel pushes it right
 *   with `ml-auto`; the essay page does the same. Layout stays with the caller.
 */
export function LevelPill({ level, className = '' }: { level: FinnishLevel; className?: string }) {
  const { L } = useLang()
  return (
    <span
      aria-label={`${L.evalLevel}: ${level}`}
      className={`inline-flex items-center gap-1.5 rounded-full bg-surface-alt border border-border
        px-2.5 py-[3px] text-[0.7rem] font-extrabold uppercase tracking-[0.5px] text-text-primary ${className}`}
    >
      <span className="text-text-muted font-semibold">{L.evalLevel}</span>
      <span>{level}</span>
    </span>
  )
}

/**
 * Titled feedback block — used for corrections and for AI prose feedback.
 * `mono` styles the body as italic accent text (used for corrections).
 */
export function FeedbackBlock({ title, body, mono }: { title: string; body: string; mono?: boolean }) {
  return (
    <div className="flex flex-col gap-1.5">
      <span className={dashSectionLabelCls}>
        {title}
      </span>
      <p
        className={`text-[0.92rem] m-0 leading-[1.55] whitespace-pre-wrap ${
          mono ? 'italic text-accent font-semibold' : 'text-text-primary'
        }`}
      >
        {body}
      </p>
    </div>
  )
}

/**
 * Submit button with a built-in pending spinner and label swap.
 */
export function EvalSubmitButton({
 enabled,
 pending,
 onClick,
 label,
 pendingLabel,
 className = '',
}: {
  enabled: boolean
  pending: boolean
  onClick: () => void
  label: string
  pendingLabel: string
  className?: string
}) {
  return (
    <button
      type="button"
      disabled={!enabled}
      onClick={onClick}
      className={`rounded-lg px-[22px] py-2.5 text-[0.9rem] font-extrabold border-none font-[inherit]
        transition-[background-color,opacity] duration-150 inline-flex items-center justify-center gap-2 ${
        enabled
          ? 'bg-accent text-white cursor-pointer hover:opacity-90'
          : 'bg-surface-alt text-text-muted cursor-not-allowed'
      } ${pending ? 'opacity-70' : ''} ${className}`}
    >
      {pending && <Spinner />}
      {pending ? pendingLabel : label}
    </button>
  )
}

/**
 * Renders a localised message for a tagged [EvaluationError.code].
 */
export function EvaluationErrorView({ code }: { code?: EvaluationErrorCode }) {
  const { L } = useLang()
  let message: string
  switch (code) {
    case 'QUOTA':
      message = L.evalErrQuota
      break
    case 'UPSTREAM':
      message = L.evalErrUpstream
      break
    case 'UNAUTHORIZED':
      message = L.evalErrUnauthorized
      break
    case 'BAD_REQUEST':
      message = L.evalErrBadRequest
      break
    default:
      message = L.evalErrGeneric
  }

  return (
    <div role="alert" className={evalErrorBoxCls}>
      {message}
    </div>
  )
}