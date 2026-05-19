import { useLang } from '../lang'
import { useState } from 'react'
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
}

export function SentenceEvaluationPanel({ word, meaning }: SentenceEvaluationPanelProps) {
  const { L } = useLang()
  const [sentence, setSentence] = useState('')
  const evaluation = useSentenceEvaluation()

  const trimmed = sentence.trim()
  const canSubmit = trimmed.length > 0 && !evaluation.isPending

  const handleSubmit = () => {
    if (!canSubmit) return
    evaluation.mutate({ sentence: trimmed, word, meaning })
  }

  return (
    <section
      aria-labelledby="eval-panel-title"
      className="w-full max-w-150 mx-auto mt-2 rounded-xl border border-border bg-surface p-5 flex flex-col gap-3.5"
    >
      <header className="flex flex-col gap-1">
        <h2 id="eval-panel-title" className="text-base font-bold text-text-primary m-0">
          {L.evalPanelTitle}
        </h2>
        <p className="text-[0.85rem] text-text-sub m-0">
          {L.evalPanelPrompt} <span className="font-bold text-text-primary">{word}</span>
        </p>
      </header>

      <label htmlFor="eval-sentence-input" className="sr-only">
        {L.evalPanelTitle}
      </label>
      <textarea
        id="eval-sentence-input"
        rows={3}
        value={sentence}
        onChange={(e) => setSentence(e.target.value)}
        placeholder={L.evalPanelPrompt}
        className={`${inputCls} resize-y min-h-[80px]`}
      />

      <button
        type="button"
        disabled={!canSubmit}
        onClick={handleSubmit}
        className="self-start rounded-lg bg-accent text-white px-5 py-2.25 text-[0.9rem] font-bold
          border-none cursor-pointer font-[inherit] transition-colors duration-150
          hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed"
      >
        {evaluation.isPending ? L.evalSubmitting : L.evalSubmit}
      </button>

      {evaluation.isError && <ErrorView code={evaluation.error.code} />}

      {evaluation.isSuccess && evaluation.data && <ResultView data={evaluation.data} />}
    </section>
  )
}

function ResultView({ data }: { data: EvaluateSentenceResponse }) {
  const { L } = useLang()
  const grammarOk = !data.hasGrammarMistake
  const typoOk = !data.hasTypo
  const wordOk = data.wordUsedCorrectly

  return (
    <div className="flex flex-col gap-3 mt-1">
      <div className="flex flex-wrap items-center gap-2">
        <Chip ok={grammarOk} label={grammarOk ? L.evalGrammarOk : L.evalGrammarBad} />
        <Chip ok={typoOk} label={typoOk ? L.evalTypoOk : L.evalTypoBad} />
        <Chip ok={wordOk} label={wordOk ? L.evalWordOk : L.evalWordBad} />
        <span
          aria-label={`${L.evalLevel}: ${data.cefrLevel}`}
          className="inline-flex items-center rounded-full bg-surface-alt border border-border
            px-2.5 py-0.5 text-[0.72rem] font-bold uppercase tracking-[0.5px] text-text-primary"
        >
          {L.evalLevel}: {data.cefrLevel}
        </span>
      </div>

      {data.correction && <FeedbackBlock title={L.evalCorrection} body={data.correction} />}

      {data.feedback && <FeedbackBlock title={L.evalFeedback} body={data.feedback} />}
    </div>
  )
}

/**
 * Small status pill. Green-ish styling when `ok`, red-ish otherwise.
 */
function Chip({ ok, label }: { ok: boolean; label: string }) {
  const cls = ok
    ? 'bg-green/15 text-green border-green/30'
    : 'bg-red/15 text-red border-red/30'
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2.5 py-0.5
        text-[0.72rem] font-bold uppercase tracking-[0.5px] ${cls}`}
    >
      {label}
    </span>
  )
}

/**
 * A titled feedback block — used for both the correction and the B1 example.
 * The `body` is plain text from the AI; we render it as-is inside a `<p>`.
 */
function FeedbackBlock({ title, body }: { title: string; body: string }) {
  return (
    <div className="flex flex-col gap-1">
      <span className={dashSectionLabelCls}>
        {title}
      </span>
      <p className="text-[0.9rem] text-text-primary m-0 leading-snug whitespace-pre-wrap">
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
      className="rounded-lg border border-red/30 bg-red/10 text-red px-3.5 py-2 text-[0.85rem] font-semibold"
    >
      {message}
    </div>
  )
}