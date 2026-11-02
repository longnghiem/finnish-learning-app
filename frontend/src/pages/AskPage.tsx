/**
 * The recruiter chatbot's page, "Ask Himmel": a human check, then the tabs Chat and Check my fit.
 *
 * Both tab panels stay mounted once there is a session; the one not shown is `hidden`. So an
 * answer or a fit check keeps streaming while the visitor looks at the other tab. Unmounting a
 * panel would abort its request, and a fit check stopped halfway still counts toward the day's
 * limit. For the same reason the fit check's stream and its draft live here, in the page: the
 * panels unmount while there is no session, e.g. after "Start a new conversation".
 */
import { type KeyboardEvent, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useLang } from '../lang'
import { useAskSession, useFitCheckStream } from '../hooks'
import { pageContainerCls, tabActiveCls, tabCls, tabInactiveCls, tabListCls } from '../styles.ts'
import { AiBadge } from '../components/EvaluationUI.tsx'
import { ChatPanel } from '../components/ChatPanel.tsx'
import { FitCheckPanel } from '../components/FitCheckPanel.tsx'
import { TurnstileGate } from '../components/TurnstileGate.tsx'

/** The tabs, in order. The URL keeps the open one as `?tab=chat|fit`, so a link can open either. */
const TABS = ['chat', 'fit'] as const
type AskTab = (typeof TABS)[number]

export function AskPage() {
  const { L } = useLang()
  const { session, create, clear } = useAskSession()
  const [searchParams, setSearchParams] = useSearchParams()
  const tab: AskTab = searchParams.get('tab') === 'fit' ? 'fit' : 'chat'
  const [sessionEnded, setSessionEnded] = useState(false)
  const fit = useFitCheckStream(session?.sessionId ?? null)
  const [fitDraft, setFitDraft] = useState(fit.jobDescription)

  const selectTab = (next: AskTab) => setSearchParams({ tab: next }, { replace: true })

  const startSession = async (token: string) => {
    await create(token)
    setSessionEnded(false)
  }

  const endSession = () => {
    clear()
    setSessionEnded(true)
  }

  return (
    <div className={pageContainerCls}>
      <header className="flex flex-col items-center gap-2 mb-6 text-center">
        <div className="flex items-center gap-2">
          <AiBadge />
          <h1 className="text-2xl font-extrabold text-text-primary m-0">{L.askTitle}</h1>
        </div>
        <p className="m-0 text-[0.92rem] text-text-sub leading-[1.55] max-w-[520px]">
          {L.askIntro}
        </p>
      </header>

      <AskTabs selected={tab} onSelect={selectTab} allPanels={session !== null} />

      {session === null ? (
        <div role="tabpanel" id={`ask-panel-${tab}`} aria-labelledby={`ask-tab-${tab}`}>
          <TurnstileGate
            onToken={startSession}
            notice={sessionEnded ? L.askGateSessionEnded : undefined}
          />
        </div>
      ) : (
        <>
          <div
            role="tabpanel"
            id="ask-panel-chat"
            aria-labelledby="ask-tab-chat"
            hidden={tab !== 'chat'}
          >
            <ChatPanel
              key={session.sessionId}
              session={session}
              onSessionExpired={endSession}
              onNewConversation={clear}
              active={tab === 'chat'}
            />
          </div>
          <div
            role="tabpanel"
            id="ask-panel-fit"
            aria-labelledby="ask-tab-fit"
            hidden={tab !== 'fit'}
          >
            <FitCheckPanel
              fit={fit}
              draft={fitDraft}
              onDraftChange={setFitDraft}
              onSessionExpired={endSession}
            />
          </div>
        </>
      )}
    </div>
  )
}

/**
 * The tab buttons, as an ARIA tab list: Tab moves focus into the list and out of it, and the
 * arrow keys move between the tabs, as screen-reader users expect.
 *
 * @param allPanels false before the human check: then only the selected tab has a panel (the
 *   check), so only that tab points at one
 */
function AskTabs({
  selected,
  onSelect,
  allPanels,
}: {
  selected: AskTab
  onSelect: (tab: AskTab) => void
  allPanels: boolean
}) {
  const { L } = useLang()
  const labels: Record<AskTab, string> = { chat: L.askTabChat, fit: L.askTabFit }

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
    const step = event.key === 'ArrowRight' ? 1 : -1
    const next = TABS[(TABS.indexOf(selected) + step + TABS.length) % TABS.length]
    onSelect(next)
    document.getElementById(`ask-tab-${next}`)?.focus()
  }

  return (
    <div role="tablist" aria-label={L.askTitle} onKeyDown={handleKeyDown} className={tabListCls}>
      {TABS.map((tab) => {
        const active = tab === selected
        return (
          <button
            key={tab}
            id={`ask-tab-${tab}`}
            type="button"
            role="tab"
            aria-selected={active}
            aria-controls={allPanels || active ? `ask-panel-${tab}` : undefined}
            tabIndex={active ? 0 : -1}
            onClick={() => onSelect(tab)}
            className={`${tabCls} ${active ? tabActiveCls : tabInactiveCls}`}
          >
            {labels[tab]}
          </button>
        )
      })}
    </div>
  )
}
