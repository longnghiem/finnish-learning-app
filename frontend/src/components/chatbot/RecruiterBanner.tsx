import { useId } from 'react'
import { Link } from 'react-router-dom'
import { useLang } from '../../lang'
import { finePrintCls, secondaryButtonCls } from '../../styles.ts'
import { HimmelMascot } from './HimmelMascot.tsx'

const primaryLinkCls =
  'rounded-lg bg-amber text-nav-btn-text px-3.5 py-2 text-[0.85rem] font-extrabold no-underline ' +
  'inline-block transition-colors duration-150 hover:bg-amber-hover'

/**
 * The invitation to the recruiter chatbot.
 *
 * {@link HimmelMascot} is decorative here: the headline already says who he is, so screen readers
 * skip the picture.
 *
 * @param className the space around the banner on the page, e.g. `mb-10`
 */
export function RecruiterBanner({ className = '' }: { className?: string }) {
  const { L } = useLang()
  const titleId = useId()

  return (
    <section
      aria-labelledby={titleId}
      className={`bg-surface border border-border rounded-2xl shadow-card px-5 py-4 flex flex-col
        sm:flex-row items-center gap-4 text-center sm:text-left ${className}`}
    >
      <HimmelMascot decorative className="w-20 sm:w-24" />
      <div className="flex-1 min-w-0 flex flex-col gap-2">
        <h2 id={titleId} className="m-0 text-[1.05rem] font-extrabold text-text-primary">
          {L.recruiterTitle}
        </h2>
        <p className="m-0 text-[0.86rem] text-text-sub leading-[1.5]">{L.recruiterText}</p>
        <div className="flex flex-wrap gap-2 justify-center sm:justify-start">
          <Link to="/ask" className={primaryLinkCls}>
            {L.recruiterAsk}
          </Link>
          <Link to="/ask?tab=fit" className={`${secondaryButtonCls} no-underline inline-block`}>
            {L.recruiterFit}
          </Link>
        </div>
        <p className={`${finePrintCls} font-semibold`}>{L.recruiterTech}</p>
      </div>
    </section>
  )
}
