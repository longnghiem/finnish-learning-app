interface ProgressDotsProps {
  total: number
  current: number
}

export function ProgressDots({ total, current }: ProgressDotsProps) {
  const capped = Math.min(total, 12)

  return (
    <div className="flex gap-1.5 items-center justify-center flex-wrap">
      {Array.from({ length: capped }, (_, i) => (
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
