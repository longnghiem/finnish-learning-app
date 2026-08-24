/**
 * Essay page: pick a prompt, write a Finnish essay, get an AI evaluation back.
 */
import { useNavigate, useParams } from 'react-router-dom'
import { useLang } from '../lang'
import { useAuth } from '../auth/useAuth.ts'
import { useEffect, useState } from 'react'
import { useEssayEvaluation, useEssayTopics, useTopics } from '../hooks'
import {
  type EssayIssue,
  type EssayPrompt,
  type EvaluateEssayResponse,
  MAX_ESSAY_LENGTH,
  MIN_ESSAY_LENGTH,
} from '../api'
import {
  backNavigationBtnCls,
  dashSectionLabelCls,
  evalPanelCls,
  evalResultCls,
  evalTextareaCls,
  inputCls,
  labelCls,
  pageContainerCls,
  pageTitleCls,
} from '../styles.ts'
import {
  AiBadge,
  Chip,
  EvalSubmitButton,
  EvaluationErrorView,
  FeedbackBlock,
  LevelPill,
} from '../components/EvaluationUI.tsx'

export function EssayPage() {
  const { topicId: topicIdParam } = useParams<{ topicId: string }>()
  const topicId = Number(topicIdParam)
  const navigate = useNavigate()
  const { L } = useLang()
  const { isLoggedIn } = useAuth()

  // Redirect unauthenticated users — both essay endpoints sit behind default-deny.
  useEffect(() => {
    if (!isLoggedIn) navigate('/login', { replace: true })
  }, [isLoggedIn, navigate])

  const { data: topics } = useTopics()
  const topicName = topics?.find((t) => t.id === topicId)?.name ?? `Topic ${topicId}`

  const prompts = useEssayTopics(topicId, isLoggedIn && Number.isFinite(topicId))
  const evaluation = useEssayEvaluation()
  const promptList = prompts.data

  const [selectedPromptId, setSelectedPromptId] = useState<number | null>(null)
  const [essay, setEssay] = useState('')

  // Default to the first prompt once the list arrives
  if (selectedPromptId === null && promptList && promptList.length > 0) {
    setSelectedPromptId(promptList[0].id)
  }

  const [prevPromptId, setPrevPromptId] = useState(selectedPromptId)
  if (prevPromptId !== selectedPromptId) {
    setPrevPromptId(selectedPromptId)
    evaluation.reset()
  }

  const trimmedEssay = essay.trim()
  const length = trimmedEssay.length
  const missing = MIN_ESSAY_LENGTH - length
  const canSubmit =
    selectedPromptId !== null &&
    length >= MIN_ESSAY_LENGTH &&
    length <= MAX_ESSAY_LENGTH &&
    !evaluation.isPending

  const handleSubmit = () => {
    if (!canSubmit) return
    evaluation.mutate({promptId: selectedPromptId, essay: trimmedEssay})
  }

  return (
    <div className={pageContainerCls}>
      <button
        type="button"
        onClick={() => navigate(`/topics/${topicId}`)}
        className={backNavigationBtnCls}
      >
        {L.backToTopic}
      </button>

      <h1 className={pageTitleCls}>
        {L.essayTitle} — {topicName}
      </h1>

      <section aria-labelledby="essay-panel-title" className={evalPanelCls}>
        <header className="flex flex-col gap-1">
          <div className="flex items-center gap-2">
            <AiBadge />
            <h2
              id="essay-panel-title"
              className="text-[1.02rem] font-extrabold text-text-primary m-0"
            >
              {L.essayTitle}
            </h2>
          </div>
          <p className="text-[0.86rem] text-text-sub m-0 leading-[1.5]">{L.essayIntro}</p>
        </header>

        <PromptPicker
          prompts={prompts}
          promptList={promptList}
          selectedPromptId={selectedPromptId}
          onSelect={setSelectedPromptId}
        />

        <div className="flex flex-col">
          <label htmlFor="essay-input" className={labelCls}>
            {L.essayTextareaLabel}
          </label>
          <textarea
            id="essay-input"
            rows={12}
            value={essay}
            maxLength={MAX_ESSAY_LENGTH}
            onChange={(e) => setEssay(e.target.value)}
            placeholder={L.essayPlaceholder}
            className={`${evalTextareaCls} min-h-[220px]`}
          />
          <div className="mt-1.5 flex items-center justify-between gap-3 flex-wrap">
            <span className="text-[0.75rem] font-semibold text-text-muted tabular-nums">
              {L.essayCounter(length, MAX_ESSAY_LENGTH)}
            </span>
            {missing > 0 && (
              <span className="text-[0.75rem] font-semibold text-amber">
                {L.essayNeedMore(missing)}
              </span>
            )}
          </div>
        </div>

        <div className="flex items-center justify-end gap-3 flex-wrap">
          {evaluation.isPending && (
            <span className="text-[0.78rem] text-text-muted font-semibold">{L.essayWaitHint}</span>
          )}
          <EvalSubmitButton
            enabled={canSubmit}
            pending={evaluation.isPending}
            onClick={handleSubmit}
            label={L.essaySubmit}
            pendingLabel={L.evalSubmitting}
          />
        </div>

        {evaluation.isError && <EvaluationErrorView code={evaluation.error?.code} />}

        {evaluation.isSuccess && evaluation.data && <EssayResultView data={evaluation.data} />}
      </section>
    </div>
  )
}

/**
 * Prompt dropdown. Loading, error and empty are rendered explicitly rather than
 * collapsing into an empty `<select>` the user cannot act on.
 */
function PromptPicker({
  prompts,
  promptList,
  selectedPromptId,
  onSelect,
}: {
  prompts: ReturnType<typeof useEssayTopics>
  promptList: EssayPrompt[] | undefined
  selectedPromptId: number | null
  onSelect: (id: number) => void
}) {
  const { L } = useLang()
  const isEmpty = !prompts.isLoading && !prompts.isError && promptList?.length === 0
  const selectedPrompt = promptList?.find((p) => p.id === selectedPromptId)

  return (
    <div className="flex flex-col">
      <label htmlFor="essay-prompt" className={labelCls}>
        {L.essayPromptLabel}
      </label>

      {prompts.isError ? (
        <p className="m-0 text-[0.85rem] font-semibold text-red">{L.essayPromptsError}</p>
      ) : isEmpty ? (
        <p className="m-0 text-[0.85rem] font-semibold text-text-muted">{L.essayNoPrompts}</p>
      ) : (
        <>
          <select
            id="essay-prompt"
            className={`${inputCls} pr-9 truncate`}
            disabled={prompts.isLoading}
            value={selectedPromptId ?? ''}
            onChange={(e) => onSelect(Number(e.target.value))}
            aria-describedby={selectedPrompt ? 'essay-prompt-full' : undefined}
          >
            {prompts.isLoading && <option value="">{L.essayPromptsLoading}</option>}
            {promptList?.map(p => (
              <option key={p.id} value={p.id}>
                {p.title}
              </option>
            ))}
          </select>

          {/* Prompts are full sentences, so the closed <select> clips them — repeat the
              selection in full underneath, where it can wrap. */}
          {selectedPrompt && (
            <p
              id="essay-prompt-full"
              className="mt-2 m-0 rounded-lg border border-dashed border-border bg-surface-alt
                px-3 py-2 text-[0.85rem] leading-[1.5] text-text-sub whitespace-pre-wrap break-words"
            >
              {selectedPrompt.title}
            </p>
          )}
        </>
      )}
    </div>
  )
}

/** Essay-specific result layout: on-topic chip, level pill, issue list, prose feedback. */
function EssayResultView({ data }: { data: EvaluateEssayResponse }) {
  const { L } = useLang()

  return (
    <div className={evalResultCls}>
      <div className="flex flex-wrap items-center gap-1.5">
        <Chip ok={data.onTopic} label={data.onTopic ? L.essayOnTopic : L.essayOffTopic} />
        <LevelPill level={data.cefrLevel} className="ml-auto" />
      </div>

      <div className="flex flex-col gap-1.5">
        <span className={dashSectionLabelCls}>{L.essayIssues}</span>
        {data.issues.length === 0 ? (
          <p className="m-0 text-[0.92rem] font-semibold text-green">{L.essayNoIssues}</p>
        ) : (
          <ul className="flex flex-col gap-2 m-0 p-0 list-none">
            {data.issues.map((issue, i) => (
              <IssueRow key={`${issue.original}-${i}`} issue={issue} />
            ))}
          </ul>
        )}
      </div>

      {data.feedback && data.feedback.trim() && (
        <FeedbackBlock title={L.evalFeedback} body={data.feedback} />
      )}
    </div>
  )
}

/**
 * One issue: the learner's fragment struck through, then the suggested replacement.
 */
function IssueRow({ issue }: { issue: EssayIssue }) {
  const { L } = useLang()
  return (
    <li className="rounded-lg border border-border bg-surface-alt px-3 py-2.5 flex flex-col gap-1">
      <span className={dashSectionLabelCls}>
        {issue.kind === 'TYPO' ? L.essayIssueTypo : L.essayIssueGrammar}
      </span>
      <p className="m-0 text-[0.92rem] leading-[1.55]">
        <span className="line-through text-red font-semibold">{issue.original}</span>
        <span aria-hidden="true" className="px-1.5 text-text-muted">→</span>
        <span className="text-green font-bold">{issue.suggestion}</span>
      </p>
    </li>
  )
}