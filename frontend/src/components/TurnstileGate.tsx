import { useEffect, useEffectEvent, useRef, useState } from 'react'
import { useLang } from '../lang'
import { useTheme } from '../theme/useTheme'
import { AskError } from '../api'
import {
  evalPanelCls,
  evalPanelIntroCls,
  evalPanelTitleCls,
  statusErrorTextCls,
  statusMutedTextCls,
} from '../styles.ts'
import { AskErrorView, ContactLinks } from './AskUI.tsx'

/** The part of Cloudflare's Turnstile script API this page uses. */
interface TurnstileApi {
  /** Draws a widget into `container` and returns its id. */
  render(container: HTMLElement, options: TurnstileOptions): string
  /** Starts a new challenge in the widget: a token can be used only once. */
  reset(widgetId: string): void
  /** Takes the widget out of the page. */
  remove(widgetId: string): void
}

interface TurnstileOptions {
  sitekey: string
  /** Called with a token when the visitor passed the check. */
  callback: (token: string) => void
  /** Called with Cloudflare's error code when the widget fails. */
  'error-callback': (code: string) => void
  /** `always`: the widget stays visible, even when it needs no click. */
  appearance: 'always'
  theme: 'light' | 'dark'
  language: string
  size: 'normal' | 'compact'
}

declare global {
  interface Window {
    turnstile?: TurnstileApi
  }
}

/** The public site key of the Turnstile widget (Cloudflare dashboard), set at build time. */
const SITE_KEY: string | undefined = import.meta.env.VITE_TURNSTILE_SITE_KEY

/** `render=explicit`: the script only loads the API; this component draws the widget. */
const SCRIPT_URL = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'

let scriptLoading: Promise<TurnstileApi> | null = null

/**
 * Loads Cloudflare's script once per page load and resolves with its API. Later calls, e.g. after
 * the visitor left `/ask` and came back, reuse it. A failed load (offline, a content blocker) can
 * be tried again.
 */
function loadTurnstile(): Promise<TurnstileApi> {
  if (window.turnstile) return Promise.resolve(window.turnstile)
  scriptLoading ??= new Promise<TurnstileApi>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = SCRIPT_URL
    script.async = true
    script.onload = () =>
      window.turnstile ? resolve(window.turnstile) : reject(new Error('Turnstile API missing'))
    script.onerror = () => {
      script.remove()
      scriptLoading = null
      reject(new Error('Turnstile script failed to load'))
    }
    document.head.appendChild(script)
  })
  return scriptLoading
}

interface TurnstileGateProps {
  /**
   * Exchanges a Turnstile token for a chat session. When it resolves, the page shows the chat
   * instead of this gate.
   *
   * @throws AskError if the backend refuses the token; the widget then starts a new challenge
   */
  onToken: (token: string) => Promise<unknown>
  /** A line above the widget, e.g. that the previous session ended. */
  notice?: string
}

/**
 * The visible "are you human?" check before a chat session (Cloudflare Turnstile).
 *
 * The widget is drawn in an effect: Turnstile is an outside system that owns its `<div>`, and
 * the effect's cleanup removes it again (also when the theme or language changes, which draws
 * it anew). When the visitor passes, Turnstile calls back with a one-time token, which goes to
 * `onToken`. If the backend refuses it, the widget is reset to get a fresh token.
 *
 * Refs hold the container node and the widget id: handles for Turnstile, never shown.
 */
export function TurnstileGate({ onToken, notice }: TurnstileGateProps) {
  const { L, lang } = useLang()
  const { dark } = useTheme()
  const containerRef = useRef<HTMLDivElement>(null)
  const widgetIdRef = useRef<string | null>(null)
  const [verifying, setVerifying] = useState(false)
  const [error, setError] = useState<AskError | null>(null)
  const [loadFailed, setLoadFailed] = useState(false)

  // An Effect Event: called from Turnstile's callback, it always sees the latest `onToken`
  // without making the effect draw the widget again when `onToken` changes.
  const verify = useEffectEvent(async (token: string) => {
    setVerifying(true)
    setError(null)
    try {
      await onToken(token)
    } catch (thrown) {
      setError(thrown instanceof AskError ? thrown : AskError.generic())
      if (widgetIdRef.current !== null) window.turnstile?.reset(widgetIdRef.current)
    } finally {
      setVerifying(false)
    }
  })

  useEffect(() => {
    if (!SITE_KEY) return
    let cancelled = false
    loadTurnstile()
      .then((turnstile) => {
        if (cancelled || containerRef.current === null) return
        widgetIdRef.current = turnstile.render(containerRef.current, {
          sitekey: SITE_KEY,
          callback: (token) => void verify(token),
          'error-callback': (code) => {
            console.warn('[TurnstileGate] Widget error', { code })
            setError(new AskError('TURNSTILE_FAILED', 'Turnstile widget error'))
          },
          appearance: 'always',
          theme: dark ? 'dark' : 'light',
          language: lang,
          // The normal widget is 300 px wide: too wide for the panel on a 375 px phone.
          size: window.matchMedia('(min-width: 400px)').matches ? 'normal' : 'compact',
        })
      })
      .catch(() => {
        if (!cancelled) setLoadFailed(true)
      })
    return () => {
      cancelled = true
      if (widgetIdRef.current !== null) {
        window.turnstile?.remove(widgetIdRef.current)
        widgetIdRef.current = null
      }
    }
  }, [dark, lang])

  return (
    <section aria-labelledby="ask-gate-title" className={evalPanelCls}>
      <h2 id="ask-gate-title" className={evalPanelTitleCls}>
        {L.askGateTitle}
      </h2>
      {notice && <p className="m-0 text-[0.86rem] font-bold text-amber">{notice}</p>}
      <p className={evalPanelIntroCls}>{L.askGateWhy}</p>

      {!SITE_KEY ? (
        <p className={statusErrorTextCls}>{L.askGateNotConfigured}</p>
      ) : loadFailed ? (
        <>
          <p className={statusErrorTextCls}>{L.askGateLoadFailed}</p>
          <ContactLinks />
        </>
      ) : (
        <div ref={containerRef} className="min-h-[65px]" />
      )}

      {verifying && <p className={statusMutedTextCls}>{L.askGateStarting}</p>}
      {error && <GateError error={error} />}
    </section>
  )
}

/**
 * Why no session was created. Two codes mean something else here than in the chat:
 * `TURNSTILE_FAILED` (the check itself) and `RATE_LIMITED` (too many new sessions per hour).
 */
function GateError({ error }: { error: AskError }) {
  const { L } = useLang()
  if (error.code === 'TURNSTILE_FAILED' || error.code === 'RATE_LIMITED') {
    return (
      <p role="alert" className={statusErrorTextCls}>
        {error.code === 'TURNSTILE_FAILED' ? L.askErrTurnstile : L.askErrTooManySessions}
      </p>
    )
  }
  return <AskErrorView error={error} />
}
