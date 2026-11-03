import { Link } from 'react-router-dom'
import { useLang } from '../lang'
import type { FitCheckStream } from '../hooks'
import { FIT_CHECKS_PER_DAY, MAX_FIT_CHECK_LENGTH } from '../api'
import {
  evalPanelCls,
  evalPanelIntroCls,
  evalPanelTitleCls,
  evalResultCls,
  evalTextareaCls,
  evalWaitHintCls,
  fieldHintCls,
  finePrintCls,
  labelCls,
  statusErrorTextCls,
  textLinkCls,
} from '../styles.ts'
import { AiBadge, EvalSubmitButton } from './EvaluationUI.tsx'
import { AskErrorView, ProgressSteps, ToolTrace } from './AskUI.tsx'
import { FitReportView } from './FitReportView.tsx'

interface FitCheckPanelProps {
  /**
   * The fit check's stream ({@link FitCheckStream}), made in the page so a running check
   * outlives this panel.
   */
  fit: FitCheckStream
  /** The job description being typed; kept in the page for the same reason. */
  draft: string
  onDraftChange: (draft: string) => void
  /** Called when the backend says the session has expired: the page asks for a new one. */
  onSessionExpired: () => void
}

/**
 * The "Check my fit" tab: a job description in, live progress, then the verified report.
 *
 * - The text may be pasted longer than 6,000 characters: the textarea has no `maxLength`, which
 *   would cut a paste off without a word. Instead the counter says it is too long and the button
 *   stays off.
 * - Enter adds a line (a job description has many); only the button starts a check.
 * - "Up to 3 checks per day" is a hint: the backend counts per IP and answers `RATE_LIMITED`.
 * - While a check runs (up to 3 minutes), the text stays editable but the button is off.
 * - The last report stays after a reload, with the text it was made for.
 * - The notice next to the button says the text is stored for 30 days, and links to `/privacy`.
 */
export function FitCheckPanel({ fit, draft, onDraftChange, onSessionExpired }: FitCheckPanelProps) {
  const { L } = useLang()

  const trimmed = draft.trim()
  const tooLong = trimmed.length > MAX_FIT_CHECK_LENGTH
  const checking = fit.status === 'streaming'
  const canCheck = !checking && trimmed !== '' && !tooLong

  const startCheck = async () => {
    if (!canCheck) return
    const failure = await fit.check(trimmed)
    if (failure?.code === 'SESSION_EXPIRED') onSessionExpired()
  }

  return (
    <section aria-labelledby="ask-fit-title" className={evalPanelCls}>
      <header className="flex flex-col gap-1">
        <div className="flex items-center gap-2">
          <AiBadge />
          <h2 id="ask-fit-title" className={evalPanelTitleCls}>
            {L.askFitTitle}
          </h2>
        </div>
        <p className={evalPanelIntroCls}>{L.askFitIntro}</p>
      </header>

      <div className="flex flex-col">
        <label htmlFor="ask-fit-input" className={labelCls}>
          {L.askFitLabel}
        </label>
        <textarea
          id="ask-fit-input"
          rows={10}
          value={draft}
          onChange={(e) => onDraftChange(e.target.value)}
          placeholder={L.askFitPlaceholder}
          aria-describedby="ask-fit-hint"
          aria-invalid={tooLong}
          className={`${evalTextareaCls} min-h-[180px]`}
        />
        <div
          id="ask-fit-hint"
          className={`${fieldHintCls} mt-1.5 flex items-center justify-between gap-3 flex-wrap`}
        >
          <span>{L.askCharCounter(trimmed.length, MAX_FIT_CHECK_LENGTH)}</span>
          <span>{L.askFitPerDay(FIT_CHECKS_PER_DAY)}</span>
        </div>
        {tooLong && (
          <p className={`${statusErrorTextCls} mt-1.5`}>{L.askFitTooLong(MAX_FIT_CHECK_LENGTH)}</p>
        )}
      </div>

      <div className="flex items-center justify-between gap-3 flex-wrap">
        <p className={`${finePrintCls} flex-1 min-w-[200px]`}>
          {L.askFitNotice}{' '}
          <Link to="/privacy" className={textLinkCls}>
            {L.askPrivacyLink}
          </Link>
        </p>
        <div className="flex items-center justify-end gap-3 flex-wrap">
          {checking && <span className={evalWaitHintCls}>{L.askFitWaitHint}</span>}
          <EvalSubmitButton
            enabled={canCheck}
            pending={checking}
            onClick={() => void startCheck()}
            label={L.askFitCheck}
            pendingLabel={L.askFitChecking}
          />
        </div>
      </div>

      {checking && fit.currentSteps.length > 0 && (
        <ProgressSteps steps={fit.currentSteps} mode="fit" />
      )}

      {fit.error && fit.error.code !== 'SESSION_EXPIRED' && (
        <AskErrorView error={fit.error} mode="fit" />
      )}

      {fit.result && (
        <div className={evalResultCls}>
          <FitReportView key={fit.result.turnId} report={fit.result.report} />
          <ToolTrace calls={fit.result.toolTrace} />
        </div>
      )}
    </section>
  )
}
