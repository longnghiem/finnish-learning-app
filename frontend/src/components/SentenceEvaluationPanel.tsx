import { useLang } from '../lang'
import { useEffect, useRef, useState } from 'react'
import { useSentenceEvaluation } from '../hooks'
import type { EvaluateSentenceResponse, EvaluationErrorCode } from '../api'
import { dashSectionLabelCls, inputCls } from '../styles'

interface SentenceEvaluationPanelProps {
  /**
   * The Finnish word from the currently flipped flashcard.
   */
  word: string
  /**
   * English translation of the word — sent to the AI for context.
   */
  meaning: string
  /**
   * When true, the textarea is auto-focused shortly after mount /
   * whenever the active word changes. Used by `TopicPage` so the user
   * can start typing the moment the eval panel becomes visible.
   */
  autoFocus?: boolean
}

export function SentenceEvaluationPanel({ word, meaning, autoFocus }: SentenceEvaluationPanelProps) {
  const { L } = useLang()
  const [sentence, setSentence] = useState('')
  const evaluation = useSentenceEvaluation()
  const inputRef = useRef<HTMLTextAreaElement>(null)

  // Reset input + reveal-focus when the active word changes (new card flipped).
  useEffect(() => {
    setSentence('')
    evaluation.reset()
    if (autoFocus && inputRef.current) {
      const t = setTimeout(() => inputRef.current?.focus(), 250)
      return () => clearTimeout(t)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [word, autoFocus])

  const trimmed = sentence.trim()
  const canSubmit = trimmed.length > 0 && !evaluation.isPending

  const handleSubmit = () => {
    if (!canSubmit) return
    evaluation.mutate({ sentence: trimmed, word, meaning })
  }

  return (
    <section
      aria-labelledby="eval-panel-title"
      className="w-full rounded-2xl border border-border bg-surface shadow-card px-[22px] pt-5 pb-[18px] flex flex-col gap-[13px]"
    >
      <header className="flex flex-col gap-1">
        <div className="flex items-center gap-2">
          <span className="text-[10px] font-extrabold text-amber tracking-[0.8px] uppercase bg-amber/15 border border-amber/35 px-2 py-[3px] rounded-md">
            AI
          </span>
          <h2 id="eval-panel-title" className="text-[1.02rem] font-extrabold text-text-primary m-0">
            {L.evalPanelTitle}
          </h2>
        </div>
        <p className="text-[0.86rem] text-text-sub m-0 leading-[1.5]">
          {L.evalPanelPrompt}{' '}
          <span className="font-extrabold text-accent">{word}</span>
          <span className="text-text-muted font-medium"> · {meaning}</span>
        </p>
      </header>

      <label htmlFor="eval-sentence-input" className="sr-only">
        {L.evalPanelTitle}
      </label>
      <textarea
        id="eval-sentence-input"
        ref={inputRef}
        rows={3}
        value={sentence}
        onChange={(e) => setSentence(e.target.value)}
        placeholder={`${L.evalPanelPrompt} ${word}…`}
        className={`${inputCls} resize-y min-h-[88px] leading-[1.55] text-[0.95rem]`}
      />

      <div className="flex items-center justify-end">
        <button
          type="button"
          disabled={!canSubmit}
          onClick={handleSubmit}
          className={`rounded-lg px-[22px] py-2.5 text-[0.9rem] font-extrabold border-none font-[inherit]
            transition-[background-color,opacity] duration-150 inline-flex items-center gap-2 ${
              canSubmit
                ? 'bg-accent text-white cursor-pointer hover:opacity-90'
                : 'bg-surface-alt text-text-muted cursor-not-allowed'
            } ${evaluation.isPending ? 'opacity-70' : ''}`}
        >
          {evaluation.isPending && <Spinner />}
          {evaluation.isPending ? L.evalSubmitting : L.evalSubmit}
        </button>
      </div>

      {evaluation.isError && <ErrorView code={evaluation.error.code} />}

      {evaluation.isSuccess && evaluation.data && <ResultView data={evaluation.data} />}
    </section>
  )
}

function Spinner() {
  return (
    <span
      aria-hidden="true"
      className="inline-block w-3 h-3 rounded-full border-2 border-white/40 border-t-white animate-spin"
    />
  )
}

function ResultView({ data }: { data: EvaluateSentenceResponse }) {
  const { L } = useLang()
  const grammarOk = !data.hasGrammarMistake
  const typoOk = !data.hasTypo
  const wordOk = data.wordUsedCorrectly

  return (
    <div className="flex flex-col gap-3.5 mt-0.5 pt-3.5 border-t border-dashed border-border animate-[fadeIn_280ms_ease]">
      <div className="flex flex-wrap items-center gap-1.5">
        <Chip ok={grammarOk} label={grammarOk ? L.evalGrammarOk : L.evalGrammarBad} />
        <Chip ok={typoOk} label={typoOk ? L.evalTypoOk : L.evalTypoBad} />
        <Chip ok={wordOk} label={wordOk ? L.evalWordOk : L.evalWordBad} />
        <span
          aria-label={`${L.evalLevel}: ${data.cefrLevel}`}
          className="ml-auto inline-flex items-center gap-1.5 rounded-full bg-surface-alt border border-border
            px-2.5 py-[3px] text-[0.7rem] font-extrabold uppercase tracking-[0.5px] text-text-primary"
        >
          <span className="text-text-muted font-semibold">{L.evalLevel}</span>
          <span>{data.cefrLevel}</span>
        </span>
      </div>

      {data.correction && data.correction.trim() && (
        <FeedbackBlock title={L.evalCorrection} body={data.correction} mono />
      )}

      {data.feedback && data.feedback.trim() && (
        <FeedbackBlock title={L.evalFeedback} body={data.feedback} />
      )}
    </div>
  )
}

/**
 * Small status pill. Green-ish when `ok`, red-ish otherwise.
 * Leading ✓ / ✕ glyph signals state at a glance.
 */
function Chip({ ok, label }: { ok: boolean; label: string }) {
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
 * Titled feedback block — used for both the correction and the AI feedback.
 * `mono` styles the body as italic accent text (used for corrections).
 */
function FeedbackBlock({ title, body, mono }: { title: string; body: string; mono?: boolean }) {
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
 * Renders a localised error message based on the tagged
 * [EvaluationError.code]. Quota and upstream failures get dedicated copy;
 * everything else falls through to the generic message.
 */
function ErrorView({ code }: { code: EvaluationErrorCode }) {
  const { L } = useLang()
  const message =
    code === 'QUOTA' ? L.evalErrQuota :
      code === 'UPSTREAM' ? L.evalErrUpstream :
        L.evalErrGeneric

  return (
    <div
      role="alert"
      className="rounded-lg border border-red/40 bg-red/10 text-red px-3.5 py-2.5 text-[0.85rem] font-semibold animate-[fadeIn_220ms_ease]"
    >
      {message}
    </div>
  )
}
