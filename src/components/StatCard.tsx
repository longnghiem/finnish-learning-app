interface StatCardProps {
  label: string
  value: string | number
  emoji?: string
  highlight?: boolean
}

/**
 * Single stat tile used at the top of the dashboard.
 *
 * - `value` accepts `string | number` so callers can either pass a raw
 *   count (`42`) or a pre-formatted string (`"85%"`).
 * - `emoji` is optional — when set, it's rendered to the right of the
 *   value (🔥 for streak, ⭐ for best streak).
 * - `highlight = true` swaps the tile background to draw attention
 *   (used by the "cards due" tile when the count is non-zero).
 */
export function StatCard({ label, value, emoji, highlight = false }: StatCardProps) {
  const tileBg = highlight ? 'bg-amber/15 border-amber' : 'bg-surface-alt border-border'

  return (
    <div className={`rounded-lg p-4 text-center border ${tileBg} transition-colors duration-150`}>
      <p className="text-[0.7rem] uppercase tracking-[0.5px] text-text-muted font-bold m-0 mb-1">
        {label}
      </p>
      <p className="text-2xl font-extrabold text-text-primary m-0 tabular-nums">
        {value}
        {emoji && <span className="ml-1.5">{emoji}</span>}
      </p>
    </div>
  )
}