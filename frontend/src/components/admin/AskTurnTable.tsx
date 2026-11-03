import { useLang } from '../../lang'
import type { AdminTurn, GuardrailOutcome, TurnStatus } from '../../schemas'
import {
  adminTdCls,
  adminThCls,
  pillAmberCls,
  pillCls,
  pillGreenCls,
  pillNeutralCls,
  pillRedCls,
  textLinkCls,
} from '../../styles.ts'
import { formatHelsinkiTime, formatUsd, preview } from './adminAskFormat.ts'

/** The colour of each status: answered green, refused or failed red, capped amber. */
const STATUS_PILL: Record<TurnStatus, string> = {
  PENDING: pillNeutralCls,
  ANSWERED: pillGreenCls,
  BLOCKED: pillRedCls,
  CAPPED: pillAmberCls,
  ERROR: pillRedCls,
}

const GUARDRAIL_PILL: Record<GuardrailOutcome, string> = {
  PASSED: pillGreenCls,
  BLOCKED: pillRedCls,
  ERROR: pillAmberCls,
}

/** How many characters of the visitor's text the table shows. */
const PREVIEW_LENGTH = 80

/** A turn's status as a coloured label. */
export function TurnStatusPill({ status }: { status: TurnStatus }) {
  const { L } = useLang()
  return <span className={`${pillCls} ${STATUS_PILL[status]}`}>{L.adminAskStatuses[status]}</span>
}

/** The input guardrail's outcome as a coloured label; a dash if the check didn't run. */
export function GuardrailPill({ outcome }: { outcome: GuardrailOutcome | null }) {
  const { L } = useLang()
  if (outcome === null) return <span className="text-text-muted">—</span>
  return (
    <span className={`${pillCls} ${GUARDRAIL_PILL[outcome]}`}>
      {L.adminAskGuardrailOutcomes[outcome]}
    </span>
  )
}

/**
 * Stored turns as a table, in the order given (newest first from the API).
 *
 * - The time is a button that opens the turn's whole session (`onOpen` with its session id).
 * - The visitor's text is cut to one line: enough to recognise a conversation from a quote in a
 *   deletion request. The full text is in the session view.
 * - For a blocked turn the guardrail column also names the categories that blocked it.
 * - The IP hash shows its first 8 characters only: enough to see that turns came from the same
 *   connection, not to recover the address.
 */
export function AskTurnTable({
  turns,
  onOpen,
}: {
  turns: readonly AdminTurn[]
  onOpen: (sessionId: string) => void
}) {
  const { lang, L } = useLang()

  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse">
        <thead>
          <tr>
            <th className={adminThCls}>{L.adminAskTime}</th>
            <th className={adminThCls}>{L.adminAskMode}</th>
            <th className={adminThCls}>{L.adminAskStatus}</th>
            <th className={adminThCls}>{L.adminAskGuardrail}</th>
            <th className={`${adminThCls} min-w-60`}>{L.adminAskMessage}</th>
            <th className={`${adminThCls} text-right`}>{L.adminAskTools}</th>
            <th className={`${adminThCls} text-right`}>{L.adminAskCost}</th>
            <th className={adminThCls}>{L.adminAskIp}</th>
          </tr>
        </thead>
        <tbody>
          {turns.map((turn, i) => {
            const time = formatHelsinkiTime(turn.createdAt, lang)
            return (
              <tr key={turn.id} className={i % 2 === 1 ? 'bg-surface-alt' : ''}>
                <td className={`${adminTdCls} whitespace-nowrap`}>
                  <button
                    type="button"
                    onClick={() => onOpen(turn.sessionId)}
                    aria-label={L.adminAskOpen(time)}
                    className={`${textLinkCls} bg-transparent border-none p-0 cursor-pointer font-[inherit] text-sm`}
                  >
                    {time}
                  </button>
                </td>
                <td className={`${adminTdCls} whitespace-nowrap`}>
                  {turn.mode === 'CHAT' ? L.adminAskModeChat : L.adminAskModeFit}
                </td>
                <td className={adminTdCls}>
                  <TurnStatusPill status={turn.status} />
                </td>
                <td className={adminTdCls}>
                  <div className="flex flex-col items-start gap-1">
                    <GuardrailPill outcome={turn.guardrailOutcome} />
                    {turn.guardrailBlockedBy.length > 0 && (
                      <span className="text-[0.72rem] font-mono text-red">
                        {turn.guardrailBlockedBy.join(', ')}
                      </span>
                    )}
                  </div>
                </td>
                <td className={`${adminTdCls} text-text-sub`}>
                  {preview(turn.userText, PREVIEW_LENGTH)}
                </td>
                <td className={`${adminTdCls} text-right font-mono tabular-nums`}>
                  {turn.toolTrace.length}
                </td>
                <td className={`${adminTdCls} text-right font-mono tabular-nums whitespace-nowrap`}>
                  {formatUsd(turn.costUsd)}
                </td>
                <td className={`${adminTdCls} font-mono text-text-muted`}>
                  {turn.ipHashPrefix ?? '—'}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
