import { useLang } from '../lang'

interface GradeButtonsProps {
  onGrade: (quality: number) => void
  isLoading: boolean
}

/**
 * Four self-grade buttons used after the user flips a quiz card.
 * Quality mapping (matches `SpacedRepetition.kt`):
 *   Again → 1, Hard → 3, Good → 4, Easy → 5.
 * All buttons are disabled while `isLoading` is true to prevent double-submission
 */
export function GradeButtons({ onGrade, isLoading }: GradeButtonsProps) {
  const { L } = useLang()

  const baseClasses =
    'flex-1 rounded-lg text-white px-3 py-3 text-[0.85rem] font-extrabold border-none cursor-pointer ' +
    'font-[inherit] transition-colors duration-150 disabled:opacity-50 disabled:cursor-not-allowed'

  return (
    <div className="flex gap-2 w-full max-w-105 mx-auto">
      <button
        type="button"
        disabled={isLoading}
        onClick={() => onGrade(1)}
        className={`${baseClasses} bg-red hover:bg-red-hover`}
      >
        {L.again}
      </button>
      <button
        type="button"
        disabled={isLoading}
        onClick={() => onGrade(3)}
        className={`${baseClasses} bg-yellow hover:bg-yellow-hover`}
      >
        {L.hard}
      </button>
      <button
        type="button"
        disabled={isLoading}
        onClick={() => onGrade(4)}
        className={`${baseClasses} bg-green hover:bg-green-hover`}
      >
        {L.good}
      </button>
      <button
        type="button"
        disabled={isLoading}
        onClick={() => onGrade(5)}
        className={`${baseClasses} bg-accent hover:opacity-90`}
      >
        {L.easy}
      </button>
    </div>
  )
}