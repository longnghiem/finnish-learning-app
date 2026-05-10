import {dashSectionLabelCls} from "../styles.ts";

interface StatCardProps {
  label: string
  value: string | number
  suffix?: string
  sub?: string
  accent?: string
  highlight?: boolean
}

/**
 * Linear-style stat tile used at the top of the dashboard.
 *
 * - `value` renders in a large monospace numeral.
 * - `suffix` (e.g. "%") sits inline next to the value.
 * - `sub` is a small caption under the number.
 * - `accent` is a top-right badge (e.g. "↗ strong").
 * - `highlight = true` swaps the tile to amber (used for non-zero "due").
 */
export function StatCard({label, value, suffix, sub, accent, highlight = false}: StatCardProps) {
  const tileCls = highlight
    ? 'bg-amber/10 border-amber'
    : 'bg-surface border-border'

  return (
    <div className={`rounded-[10px] border ${tileCls} px-5 py-5 flex flex-col gap-3`}>
      <div className="flex items-center justify-between">
        <span className={dashSectionLabelCls}>{label}</span>
        {accent && (
          <span className="text-[11px] font-mono font-semibold text-green tabular-nums">
            {accent}
          </span>
        )}
      </div>
      <div className="flex items-baseline gap-1.5">
        <span className="text-[32px] font-semibold font-mono text-text-primary leading-none tracking-[-1px] tabular-nums">
          {value}
        </span>
        {suffix && (
          <span className="text-sm font-medium text-text-muted font-mono">{suffix}</span>
        )}
      </div>
      {sub && (
        <span className="text-[11.5px] text-text-muted font-mono tabular-nums">{sub}</span>
      )}
    </div>
  )
}