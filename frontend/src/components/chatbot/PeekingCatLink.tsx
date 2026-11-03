import { Link, useLocation } from 'react-router-dom'
import { useLang } from '../../lang'
import { isPathActive } from '../../utils/routeUtils.ts'
import { HimmelMascot } from './HimmelMascot.tsx'

/** Pages that already invite recruiters (the banner) or are the chatbot itself. */
const PAGES_WITH_OWN_INVITATION = ['/portfolio', '/ask']

/**
 * True on the pages that don't get the peeking cat: the home page (checked with `===`, because
 * the root matches every path in {@link isPathActive}) and {@link PAGES_WITH_OWN_INVITATION}.
 */
function hasOwnInvitation(pathname: string): boolean {
  return pathname === '/' || PAGES_WITH_OWN_INVITATION.some((page) => isPathActive(pathname, page))
}

/**
 * A small link to the recruiter chatbot on every other page: Himmel peeking up from the bottom
 * right corner. Hover or keyboard focus slides him up and shows a speech bubble beside him.
 *
 * - At rest, 31 % of the link is below the screen's edge, so his ears and eyes show. Raised, 16 %
 *   is: the peek frame has empty space under the paws, and this puts them on the edge.
 * - Rendered once, next to the routes; it hides itself where the page has its own invitation.
 * - From `sm` (640 px) up only: on a phone, a fixed corner picture would cover the page's own
 *   buttons, and the navbar's "Ask Himmel" pill is there anyway.
 * - Below the navbar (`z-20`) and the modals (`z-50`).
 * - The link's name comes from `aria-label`; Himmel and the bubble are hidden from screen readers.
 */
export function PeekingCatLink() {
  const { pathname } = useLocation()
  const { L } = useLang()

  if (hasOwnInvitation(pathname)) return null

  return (
    <Link
      to="/ask"
      aria-label={L.peekLabel}
      className="group fixed bottom-0 right-6 z-10 hidden sm:block rounded-t-xl translate-y-[31%]
        hover:translate-y-[16%] focus-visible:translate-y-[16%] transition-transform duration-200
        ease-out motion-reduce:transition-none focus-visible:outline-2 focus-visible:outline-offset-2
        focus-visible:outline-accent"
    >
      <span
        aria-hidden="true"
        className="absolute right-full top-[30%] mr-1.5 whitespace-nowrap rounded-lg rounded-tr-sm
          border border-border bg-surface px-2.5 py-1 text-[0.75rem] font-bold text-text-primary
          shadow-card opacity-0 transition-opacity duration-200 group-hover:opacity-100
          group-focus-visible:opacity-100 motion-reduce:transition-none"
      >
        {L.peekBubble}
      </span>
      <HimmelMascot variant="peek" decorative className="block w-24" />
    </Link>
  )
}
