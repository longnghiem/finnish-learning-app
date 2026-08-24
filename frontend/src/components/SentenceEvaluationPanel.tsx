import { useLang } from '../lang'
import { useEffect, useRef, useState } from 'react'
import { useSentenceEvaluation } from '../hooks'
import type { EvaluateSentenceResponse } from '../api'
import { evalPanelCls, evalResultCls, evalTextareaCls } from '../styles'
import {
  AiBadge,
  Chip,
  EvalSubmitButton,
  EvaluationErrorView,
  FeedbackBlock,
  LevelPill,
} from './EvaluationUI.tsx'

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

export function SentenceEvaluationPanel({
  word,
  meaning,
  autoFocus,
}: SentenceEvaluationPanelProps) {
  const { L } = useLang()
  const [sentence, setSentence] = useState('')
  const [prevWord, setPrevWord] = useState(word)
  const evaluation = useSentenceEvaluation()
  const inputRef = useRef<HTMLTextAreaElement>(null)

  if (prevWord !== word) {
    setPrevWord(word)
    setSentence('')
    evaluation.reset()
  }

  useEffect(() => {
    if (autoFocus && inputRef.current) {
      const t = setTimeout(() => inputRef.current?.focus(), 250)
      return () => clearTimeout(t)
    }
  }, [word, autoFocus])

  const trimmed = sentence.trim()
  const canSubmit = trimmed.length > 0 && !evaluation.isPending

  const handleSubmit = () => {
    if (!canSubmit) return
    evaluation.mutate({ sentence: trimmed, word, meaning })
  }

  return (
    <section aria-labelledby="eval-panel-title" className={evalPanelCls}>
      <header className="flex flex-col gap-1">
        <div className="flex items-center gap-2">
          <AiBadge />
          <h2 id="eval-panel-title" className="text-[1.02rem] font-extrabold text-text-primary m-0">
            {L.evalPanelTitle}
          </h2>
        </div>
        <p className="text-[0.86rem] text-text-sub m-0 leading-[1.5]">
          {L.evalPanelPrompt} <span className="font-extrabold text-accent">{word}</span>
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
        className={`${evalTextareaCls} min-h-[88px]`}
      />

      <div className="flex items-center justify-end">
        <EvalSubmitButton
          enabled={canSubmit}
          pending={evaluation.isPending}
          onClick={handleSubmit}
          label={L.evalSubmit}
          pendingLabel={L.evalSubmitting}
        />
      </div>

      {evaluation.isError && <EvaluationErrorView code={evaluation.error?.code} />}

      {evaluation.isSuccess && evaluation.data && <ResultView data={evaluation.data} />}
    </section>
  )
}

/** Sentence-specific result layout: three boolean chips, a level pill, correction, feedback. */
function ResultView({ data }: { data: EvaluateSentenceResponse }) {
  const { L } = useLang()
  const grammarOk = !data.hasGrammarMistake
  const typoOk = !data.hasTypo
  const wordOk = data.wordUsedCorrectly

  return (
    <div className={evalResultCls}>
      <div className="flex flex-wrap items-center gap-1.5">
        <Chip ok={grammarOk} label={grammarOk ? L.evalGrammarOk : L.evalGrammarBad} />
        <Chip ok={typoOk} label={typoOk ? L.evalTypoOk : L.evalTypoBad} />
        <Chip ok={wordOk} label={wordOk ? L.evalWordOk : L.evalWordBad} />
        <LevelPill level={data.cefrLevel} className="ml-auto" />
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