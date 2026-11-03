/**
 * Himmel, the chatbot's mascot: a Birman cat with a cup of coffee. The artwork is a pair of
 * transparent WebP frames (eyes open, eyes closed) in `src/assets/mascot/`; until they exist, an
 * SVG drawing stands in. Both are animated by the same CSS (`HimmelMascot.module.css`).
 */
import { useId } from 'react'
import { useLang } from '../../lang'
import type { MascotState } from './mascotState.ts'
import styles from './HimmelMascot.module.css'

/**
 * The artwork that exists, by file name. Vite resolves the pattern when it builds: every match
 * becomes a URL, and no match gives an empty object, so a build without the files still works.
 */
const FRAMES: Readonly<Record<string, string>> = import.meta.glob('../../assets/mascot/*.webp', {
  eager: true,
  query: '?url',
  import: 'default',
})

/** The URL of one frame, e.g. `bust-eyes-open`, or undefined if the file isn't there. */
function frameUrl(name: string): string | undefined {
  return FRAMES[`../../assets/mascot/${name}.webp`]
}

interface HimmelMascotProps {
  /** What Himmel is doing; each state has its own animation. Default `idle`. */
  state?: MascotState
  /**
   * `bust`: sitting with the cup, for the Ask page and the banner. `peek`: only the head and
   * paws, looking over an edge, for a small link. Default `bust`.
   */
  variant?: 'bust' | 'peek'
  /**
   * True when the text next to Himmel already says what he is for (e.g. inside a link with its
   * own label): he is then hidden from screen readers.
   */
  decorative?: boolean
  /** Classes for the size and the place, e.g. `w-24`. They win over the component's defaults. */
  className?: string
}

/**
 * Himmel the cat, animated by state.
 *
 * - `idle`: breathing, a blink every few seconds, steam over the cup;
 * - `listening`: leans in a little;
 * - `thinking`: the head tilts back and forth, the steam rises faster;
 * - `talking`: a short bob when an answer arrives, then calm again;
 * - `sleeping`: eyes closed, no steam, a floating "z".
 *
 * The root has the one CSS class; the parts are found by `data-himmel-part`, and the state and
 * variant by `data-state` and `data-variant`. With `prefers-reduced-motion`, nothing moves: the
 * state still shows in the still picture (closed eyes and a "z" when sleeping).
 */
export function HimmelMascot({
  state = 'idle',
  variant = 'bust',
  decorative = false,
  className = '',
}: HimmelMascotProps) {
  const { L } = useLang()
  const stateLabels: Record<MascotState, string> = {
    idle: L.himmelIdle,
    listening: L.himmelListening,
    thinking: L.himmelThinking,
    talking: L.himmelTalking,
    sleeping: L.himmelSleeping,
  }
  const a11y = decorative
    ? { 'aria-hidden': true }
    : { role: 'img', 'aria-label': L.himmelLabel(stateLabels[state]) }

  return (
    <span
      className={`${styles.himmel} ${className}`}
      data-state={state}
      data-variant={variant}
      {...a11y}
    >
      <span data-himmel-part="figure">{variant === 'bust' ? <BustArt /> : <PeekArt />}</span>
      {variant === 'bust' && <Steam />}
      <span data-himmel-part="z">z</span>
    </span>
  )
}

/** The sitting cat with the cup: the two frames if they exist, else the drawing. */
function BustArt() {
  const open = frameUrl('bust-eyes-open')
  const closed = frameUrl('bust-eyes-closed')
  if (open === undefined || closed === undefined) return <BustDrawing />
  return (
    <>
      <img data-himmel-part="eyes-open" src={open} alt="" draggable={false} />
      <img data-himmel-part="eyes-closed" src={closed} alt="" draggable={false} />
    </>
  )
}

/** The peeking cat: the frame if it exists, else the drawing. */
function PeekArt() {
  const peek = frameUrl('peek')
  if (peek === undefined) return <PeekDrawing />
  return <img data-himmel-part="frame" src={peek} alt="" draggable={false} />
}

/** Three wisps of steam, rising from the cup and drifting left, past the cat's cheek. */
function Steam() {
  return (
    <svg data-himmel-part="steam" viewBox="0 0 40 60" fill="none">
      <path d="M26 58 C 22 48, 30 42, 20 34 S 6 22, 4 8" />
      <path d="M33 58 C 29 50, 37 44, 27 36 S 13 22, 10 4" />
      <path d="M39 56 C 37 48, 43 42, 34 34 S 21 20, 17 6" />
    </svg>
  )
}

/** Fur, mask and cup colours of the drawing, the same in both themes. */
const CREAM = '#f3e7d3'
const POINT = '#5c3b28'
const POINT_SOFT = '#8a6248'
const PINK = '#e7a0a0'

/** Eyes open and eyes closed, at the same place, for the blink. */
function Eyes() {
  return (
    <>
      <g data-himmel-part="eyes-open">
        <ellipse cx="49" cy="54" rx="3.4" ry="4" fill="#2a3b5c" />
        <ellipse cx="71" cy="54" rx="3.4" ry="4" fill="#2a3b5c" />
        <circle cx="50.2" cy="52.6" r="1.1" fill="#ffffff" />
        <circle cx="72.2" cy="52.6" r="1.1" fill="#ffffff" />
      </g>
      <g data-himmel-part="eyes-closed" stroke={POINT} strokeWidth="2" strokeLinecap="round">
        <path d="M44.5 54 Q 49 58.5 53.5 54" />
        <path d="M66.5 54 Q 71 58.5 75.5 54" />
      </g>
    </>
  )
}

/**
 * The cat's head: ears, face, the dark Birman mask, eyes, nose and mouth.
 *
 * @param maskId the id of the drawing's {@link MaskGradient}
 */
function Head({ maskId }: { maskId: string }) {
  return (
    <>
      <path d="M33 44 L 36 16 L 54 32 Z" fill={POINT} />
      <path d="M87 44 L 84 16 L 66 32 Z" fill={POINT} />
      <path d="M37 38 L 38.5 22 L 49 31.5 Z" fill={PINK} />
      <path d="M83 38 L 81.5 22 L 71 31.5 Z" fill={PINK} />
      <ellipse cx="60" cy="52" rx="30" ry="25" fill={CREAM} />
      <ellipse cx="60" cy="58" rx="17" ry="14" fill={`url(#${maskId})`} />
      <Eyes />
      <path d="M57 61 L 63 61 L 60 64.5 Z" fill={PINK} />
      <path
        d="M60 64.5 Q 57 68 54 66 M60 64.5 Q 63 68 66 66"
        stroke={POINT}
        strokeWidth="1.3"
        strokeLinecap="round"
        fill="none"
      />
    </>
  )
}

/**
 * The mask's gradient: dark at the nose, fading into the cream fur. Its id comes from
 * {@link useId}, so two drawings on one page (e.g. a banner and a peeking cat) don't share one id.
 */
function MaskGradient({ id }: { id: string }) {
  return (
    <defs>
      <radialGradient id={id}>
        <stop offset="0%" stopColor={POINT} />
        <stop offset="55%" stopColor={POINT_SOFT} stopOpacity="0.8" />
        <stop offset="100%" stopColor={POINT_SOFT} stopOpacity="0" />
      </radialGradient>
    </defs>
  )
}

/** The stand-in for the bust frames: the cat sitting, both paws on a cup held a little left. */
function BustDrawing() {
  const maskId = useId()
  return (
    <svg viewBox="0 0 120 120">
      <MaskGradient id={maskId} />
      <ellipse cx="60" cy="112" rx="40" ry="34" fill={CREAM} />
      <Head maskId={maskId} />
      <path d="M38 84 L 66 84 L 63 104 Q 52 108 41 104 Z" fill="#ffffff" stroke={POINT_SOFT} />
      <ellipse cx="52" cy="84" rx="14" ry="3" fill="#7a4a2a" stroke={POINT_SOFT} />
      <circle cx="36" cy="95" r="6.5" fill="#ffffff" stroke={POINT_SOFT} />
      <circle cx="68" cy="95" r="6.5" fill="#ffffff" stroke={POINT_SOFT} />
    </svg>
  )
}

/** The stand-in for the peek frame: the head and two paws over an edge. */
function PeekDrawing() {
  const maskId = useId()
  return (
    <svg viewBox="12 10 96 64">
      <MaskGradient id={maskId} />
      <Head maskId={maskId} />
      <circle cx="42" cy="72" r="7" fill="#ffffff" stroke={POINT_SOFT} />
      <circle cx="78" cy="72" r="7" fill="#ffffff" stroke={POINT_SOFT} />
    </svg>
  )
}
