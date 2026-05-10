interface ProgressBarProps {
  total: number
  current: number
}

export function ProgressBar({ total, current }: ProgressBarProps) {
  if (total > 12) {
    const pct = total > 0 ? ((current + 1) / total) * 100 : 0
    return (
      <div className="flex flex-col items-center gap-1 w-full max-w-xs mx-auto">
        <div className="h-2 w-full rounded-full bg-border overflow-hidden">
          <div
            className="h-full bg-accent transition-[width] duration-200"
            style={{ width: `${pct}%` }}
          />
        </div>
        <span className="text-xs text-muted-foreground">
          {current + 1} / {total}
        </span>
      </div>
    )
  }

  return (
    <div className="flex gap-1.5 items-center justify-center flex-wrap">
      {Array.from({ length: total }, (_, i) => (
        <span
          key={i}
          className={`inline-block w-2 h-2 rounded-full transition-[background,transform] duration-200 ${
            i === current ? 'bg-accent scale-[1.4]' : 'bg-border scale-100'
          }`}
        />
      ))}
    </div>
  )
}
