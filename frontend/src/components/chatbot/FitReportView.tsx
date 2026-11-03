/**
 * The verified fit report of a job description: summary, one row per requirement, gaps and a
 * note on how the quotes were checked. Used by the "Check my fit" tab.
 */
import { useState } from 'react'
import { type I18nStrings, useLang } from '../../lang'
import type { FitEvidence, FitReport, FitRequirement, FitVerdict } from '../../schemas'
import {
  dashSectionLabelCls,
  evalListItemCls,
  fieldHintCls,
  finePrintCls,
  pillAmberCls,
  pillCls,
  pillGreenCls,
  pillNeutralCls,
  pillRedCls,
  secondaryButtonCls,
  statusErrorTextCls,
  statusMutedTextCls,
} from '../../styles.ts'
import { InlineText } from './AskUI.tsx'
import { FeedbackBlock } from '../EvaluationUI.tsx'

/** The order of the rows: the strongest matches first. */
const VERDICT_ORDER: readonly FitVerdict[] = ['STRONG', 'PARTIAL', 'GAP', 'NOT_IN_PROFILE']

/** Colour and glyph of each verdict. The glyph and the word keep it readable without colour. */
const VERDICT_LOOK: Record<FitVerdict, { pill: string; glyph: string }> = {
  STRONG: { pill: pillGreenCls, glyph: '✓' },
  PARTIAL: { pill: pillAmberCls, glyph: '◐' },
  GAP: { pill: pillRedCls, glyph: '✕' },
  NOT_IN_PROFILE: { pill: pillNeutralCls, glyph: '–' },
}

function verdictLabels(L: I18nStrings): Record<FitVerdict, string> {
  return {
    STRONG: L.askVerdictStrong,
    PARTIAL: L.askVerdictPartial,
    GAP: L.askVerdictGap,
    NOT_IN_PROFILE: L.askVerdictNotInProfile,
  }
}

/**
 * The rows, strongest first, as a sorted copy. `sort` is stable, so rows with the same verdict
 * keep the job description's order. A spread copy rather than `toSorted`, which older browsers
 * (before Safari 16, Chrome 110) don't have.
 */
function sortedRows(requirements: readonly FitRequirement[]): readonly FitRequirement[] {
  return [...requirements].sort(
    (a, b) => VERDICT_ORDER.indexOf(a.verdict) - VERDICT_ORDER.indexOf(b.verdict),
  )
}

/** How the quotes were checked, and how many were removed. */
function footnoteOf(report: FitReport, L: I18nStrings): string {
  return report.unverifiedQuotesRemoved > 0
    ? `${L.askFitFootnote} ${L.askFitQuotesRemoved(report.unverifiedQuotesRemoved)}`
    : L.askFitFootnote
}

/** The report as plain text, in the order the page shows it, for the clipboard. */
function reportAsText(report: FitReport, L: I18nStrings): string {
  const labels = verdictLabels(L)
  const rows = sortedRows(report.requirements).flatMap((row) => [
    `[${labels[row.verdict]}] ${row.requirement}`,
    ...row.evidence.map((evidence) => `  "${evidence.quote}" — ${evidence.entryTitle}`),
  ])
  const gaps = report.gaps.map((gap) => `- ${gap}`)
  return [
    L.askFitSummary,
    report.summary,
    '',
    L.askFitRequirements,
    ...(rows.length === 0 ? [L.askFitNoRequirements] : rows),
    '',
    L.askFitGaps,
    ...(gaps.length === 0 ? [L.askFitNoGaps] : gaps),
    '',
    footnoteOf(report, L),
  ].join('\n')
}

/**
 * A fit report, as the backend sent it after checking every quote word for word.
 *
 * - The summary comes first. When the check lowered a requirement, the backend has already
 *   replaced the model's summary with one built from the checked rows, so it is shown as it is.
 * - Rows are sorted by verdict. Every quote is shown: all of them passed the check. `GAP` and
 *   `NOT_IN_PROFILE` rows have none.
 * - Gaps are a list of their own, also when empty: they may name things that have no row.
 * - The footnote says how quotes were checked, and how many were removed.
 *
 * Everything is rendered as text; {@link InlineText} shows the model's `**bold**` as bold.
 * "Copy report" puts the same content on the clipboard, where the stars stay: plain text's usual
 * way to mark bold.
 */
export function FitReportView({ report }: { report: FitReport }) {
  const { L } = useLang()
  const [copy, setCopy] = useState<'idle' | 'copied' | 'failed'>('idle')
  const rows = sortedRows(report.requirements)

  const copyReport = async () => {
    try {
      await navigator.clipboard.writeText(reportAsText(report, L))
      setCopy('copied')
    } catch (thrown) {
      // The report itself is never logged.
      console.warn('[FitReportView] Copy failed', {
        name: thrown instanceof Error ? thrown.name : typeof thrown,
      })
      setCopy('failed')
    }
  }

  return (
    <div className="flex flex-col gap-3.5">
      <FeedbackBlock title={L.askFitSummary} body={<InlineText text={report.summary} />} />

      <div className="flex flex-col gap-1.5">
        <span className={dashSectionLabelCls}>{L.askFitRequirements}</span>
        {rows.length === 0 ? (
          <p className={statusMutedTextCls}>{L.askFitNoRequirements}</p>
        ) : (
          <ul className="flex flex-col gap-2 m-0 p-0 list-none">
            {rows.map((row, i) => (
              <RequirementRow key={i} row={row} />
            ))}
          </ul>
        )}
      </div>

      <div className="flex flex-col gap-1.5">
        <span className={dashSectionLabelCls}>{L.askFitGaps}</span>
        {report.gaps.length === 0 ? (
          <p className={statusMutedTextCls}>{L.askFitNoGaps}</p>
        ) : (
          <ul className="m-0 pl-5 list-disc flex flex-col gap-1 text-[0.92rem] leading-[1.5] text-text-primary">
            {report.gaps.map((gap, i) => (
              <li key={i}>
                <InlineText text={gap} />
              </li>
            ))}
          </ul>
        )}
      </div>

      <p className={finePrintCls}>{footnoteOf(report, L)}</p>

      <div className="flex items-center gap-3 flex-wrap">
        <button type="button" onClick={() => void copyReport()} className={secondaryButtonCls}>
          {L.askFitCopy}
        </button>
        <p role="status" className={copy === 'failed' ? statusErrorTextCls : statusMutedTextCls}>
          {copy === 'copied' && L.askFitCopied}
          {copy === 'failed' && L.askFitCopyFailed}
        </p>
      </div>
    </div>
  )
}

/** One requirement: its verdict, its text and the quotes that back it. */
function RequirementRow({ row }: { row: FitRequirement }) {
  return (
    <li className={evalListItemCls}>
      <div className="flex items-start gap-2 flex-wrap">
        <VerdictBadge verdict={row.verdict} />
        <span className="min-w-0 text-[0.92rem] font-semibold leading-[1.45] text-text-primary">
          <InlineText text={row.requirement} />
        </span>
      </div>
      {row.evidence.map((evidence, i) => (
        <Quote key={i} evidence={evidence} />
      ))}
    </li>
  )
}

/** A quote from the profile, with the title of the entry it comes from. */
function Quote({ evidence }: { evidence: FitEvidence }) {
  return (
    <figure className="m-0 mt-1 flex flex-col gap-0.5">
      <blockquote className="m-0 border-l-2 border-accent pl-3 text-[0.88rem] italic leading-[1.5] text-text-sub whitespace-pre-wrap">
        <InlineText text={evidence.quote} />
      </blockquote>
      <figcaption className={`${fieldHintCls} pl-3`}>— {evidence.entryTitle}</figcaption>
    </figure>
  )
}

/**
 * A verdict as a pill: colour, glyph and word, so it doesn't rely on colour alone.
 * - `STRONG` (✓, green): a quote shows Long did this at the level the job asks for;
 * - `PARTIAL` (◐, amber): something related, a lower level, or shown only indirectly;
 * - `GAP` (✕, red): the profile says Long hasn't done this;
 * - `NOT_IN_PROFILE` (–, grey): no evidence either way, or no quote passed the check.
 */
function VerdictBadge({ verdict }: { verdict: FitVerdict }) {
  const { L } = useLang()
  const look = VERDICT_LOOK[verdict]
  return (
    <span className={`${pillCls} ${look.pill} shrink-0`}>
      <span aria-hidden="true">{look.glyph}</span>
      {verdictLabels(L)[verdict]}
    </span>
  )
}
