import { useState } from 'react'
import { useLang } from '../lang'
import { AdminAskError } from '../api'
import { useAdminSession, useDeleteAdminSession } from '../hooks'
import type { AdminTurn } from '../schemas'
import {
  adminTdCls,
  adminThCls,
  backNavigationBtnCls,
  dashSectionLabelCls,
  evalErrorBoxCls,
  evalListItemCls,
  evalPanelCls,
  evalPanelTitleCls,
  finePrintCls,
  pillCls,
  pillNeutralCls,
  statusErrorTextCls,
  statusMutedTextCls,
} from '../styles.ts'
import { ConfirmModal } from './ConfirmModal.tsx'
import { FitReportView } from './FitReportView.tsx'
import { ToolTrace } from './AskUI.tsx'
import { GuardrailPill, TurnStatusPill } from './AskTurnTable.tsx'
import { formatHelsinkiTime, formatUsd, localeOf } from './adminAskFormat.ts'

/** A stored text (a message, an answer, a job description) as plain text, line breaks kept. */
const storedTextCls =
  'm-0 rounded-lg border border-border bg-surface-alt px-3 py-2.5 text-[0.86rem] ' +
  'text-text-primary leading-[1.55] whitespace-pre-wrap break-words'

/**
 * One chat session with all its turns, oldest first, as the admin page's chatbot tab shows it,
 * and the button that deletes it.
 *
 * - Every text is shown as stored, as plain text with its line breaks (`whitespace-pre-wrap`),
 *   never as HTML: an attack written into a message shows its characters and runs nothing.
 * - A fit check's report is shown with {@link FitReportView}, the view the visitor saw.
 * - Deleting asks first ({@link ConfirmModal}); after it, `onBack` returns to the turn list,
 *   which loads again without this session's turns.
 * - A session that no longer exists (deleted, or purged after 30 days) gets a plain notice.
 *
 * @param onBack called by the back link and after a delete
 */
export function AskSessionDetail({ sessionId, onBack }: { sessionId: string; onBack: () => void }) {
  const { lang, L } = useLang()
  const { data: session, error, isPending } = useAdminSession(sessionId)
  const deleteSession = useDeleteAdminSession()
  const [confirming, setConfirming] = useState(false)

  const notFound = error instanceof AdminAskError && error.status === 404
  const totalCost = session?.turns.reduce((sum, turn) => sum + turn.costUsd, 0) ?? 0

  return (
    <div className="flex flex-col gap-4">
      <button type="button" onClick={onBack} className={`${backNavigationBtnCls} mb-0`}>
        {L.adminAskBack}
      </button>

      {isPending ? (
        <p className={statusMutedTextCls}>{L.adminAskLoading}</p>
      ) : notFound ? (
        <p className={statusMutedTextCls}>{L.adminAskNotFound}</p>
      ) : error ? (
        <p className={statusErrorTextCls}>{error.message}</p>
      ) : (
        <>
          <header className="flex items-start justify-between gap-3 flex-wrap">
            <div className="flex flex-col gap-1 min-w-0">
              <h2 className={evalPanelTitleCls}>{L.adminAskSessionTitle}</h2>
              <p className={`${finePrintCls} font-mono break-all`}>{session.id}</p>
              <p className={finePrintCls}>
                {L.adminAskSessionMeta(
                  formatHelsinkiTime(session.createdAt, lang),
                  session.ipHashPrefix,
                  session.turns.length,
                  formatUsd(totalCost),
                )}
              </p>
            </div>
            <button
              type="button"
              onClick={() => setConfirming(true)}
              className="rounded-lg bg-red text-white px-4 py-2 text-sm font-bold border-none cursor-pointer font-[inherit]"
            >
              {L.adminAskDeleteSession}
            </button>
          </header>

          {deleteSession.error && (
            <p className={evalErrorBoxCls}>
              {L.adminAskDeleteFailed} {deleteSession.error.message}
            </p>
          )}

          {session.turns.map((turn, i) => (
            <TurnDetail key={turn.id} turn={turn} index={i + 1} />
          ))}
        </>
      )}

      {confirming && (
        <ConfirmModal
          message={L.adminAskDeleteConfirm}
          onConfirm={() =>
            deleteSession.mutate(sessionId, {
              onSuccess: onBack,
              onSettled: () => setConfirming(false),
            })
          }
          onCancel={() => setConfirming(false)}
          isLoading={deleteSession.isPending}
        />
      )}
    </div>
  )
}

/** One turn with everything stored about it. */
function TurnDetail({ turn, index }: { turn: AdminTurn; index: number }) {
  const { lang, L } = useLang()
  const locale = localeOf(lang)
  const fit = turn.mode === 'FIT_CHECK'
  const { inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens } = turn.tokens

  return (
    <article className={evalPanelCls} aria-label={L.adminAskTurnLabel(index)}>
      <div className="flex items-center gap-2 flex-wrap">
        <span className="text-[0.88rem] font-extrabold text-text-primary">#{index}</span>
        <span className={finePrintCls}>{formatHelsinkiTime(turn.createdAt, lang)}</span>
        <span className={`${pillCls} ${pillNeutralCls}`}>
          {fit ? L.adminAskModeFit : L.adminAskModeChat}
        </span>
        <TurnStatusPill status={turn.status} />
        <GuardrailPill outcome={turn.guardrailOutcome} />
      </div>

      <section className="flex flex-col gap-1.5">
        <h3 className={dashSectionLabelCls}>
          {fit ? L.adminAskJobDescription : L.adminAskUserText}
        </h3>
        <p className={storedTextCls}>{turn.userText}</p>
      </section>

      <section className="flex flex-col gap-1.5">
        <h3 className={dashSectionLabelCls}>{L.adminAskAnswer}</h3>
        {turn.fitReport ? (
          <FitReportView report={turn.fitReport} />
        ) : turn.answerText ? (
          <p className={storedTextCls}>{turn.answerText}</p>
        ) : (
          <p className={statusMutedTextCls}>{L.adminAskNoAnswer}</p>
        )}
      </section>

      <ToolTrace calls={turn.toolTrace} defaultOpen />

      <dl className="m-0 grid grid-cols-1 sm:grid-cols-2 gap-2">
        <Fact label={L.adminAskCost} value={formatUsd(turn.costUsd)} />
        <Fact
          label={L.adminAskLatency}
          value={turn.latencyMs === null ? '—' : `${turn.latencyMs.toLocaleString(locale)} ms`}
        />
        <Fact
          label={L.adminAskTokens}
          value={L.adminAskTokensValue(
            inputTokens.toLocaleString(locale),
            outputTokens.toLocaleString(locale),
            cacheReadTokens.toLocaleString(locale),
            cacheWriteTokens.toLocaleString(locale),
          )}
        />
        <Fact label={L.adminAskTextUnits} value={String(turn.guardrailTextUnits)} />
      </dl>

      <GuardrailScores scores={turn.guardrailScores} blockedBy={turn.guardrailBlockedBy} />
    </article>
  )
}

/** One label and value of a turn's figures. */
function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div className={evalListItemCls}>
      <dt className={dashSectionLabelCls}>{label}</dt>
      <dd className="m-0 text-[0.86rem] font-mono tabular-nums text-text-primary">{value}</dd>
    </div>
  )
}

/**
 * The guardrail's score per category, highest first. The categories that blocked the turn
 * (`blockedBy`, computed by the backend with today's thresholds) are marked in red.
 */
function GuardrailScores({
  scores,
  blockedBy,
}: {
  scores: Record<string, number> | null
  blockedBy: readonly string[]
}) {
  const { L } = useLang()

  if (scores === null || Object.keys(scores).length === 0) {
    return <p className={finePrintCls}>{L.adminAskNoScores}</p>
  }
  const rows = Object.entries(scores).sort(([, a], [, b]) => b - a)

  return (
    <section className="flex flex-col gap-1.5">
      <h3 className={dashSectionLabelCls}>{L.adminAskScores}</h3>
      <div className="overflow-x-auto rounded-lg border border-border">
        <table className="w-full border-collapse">
          <thead>
            <tr>
              <th className={adminThCls}>{L.adminAskCategory}</th>
              <th className={`${adminThCls} text-right`}>{L.adminAskScore}</th>
            </tr>
          </thead>
          <tbody>
            {rows.map(([category, score]) => {
              const blocked = blockedBy.includes(category)
              return (
                <tr key={category}>
                  <td className={`${adminTdCls} font-mono ${blocked ? 'text-red font-bold' : ''}`}>
                    {category}
                    {blocked && ` · ${L.adminAskBlockedBy}`}
                  </td>
                  <td
                    className={`${adminTdCls} text-right font-mono tabular-nums ${blocked ? 'text-red font-bold' : ''}`}
                  >
                    {score.toFixed(2)}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </section>
  )
}
