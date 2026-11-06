/**
 * The recruiter chatbot's page, "Ask Himmel": Himmel the mascot, a human check, then the tabs Chat
 * and the fit check. Under them, the live stats and "How this works".
 *
 * Both tab panels stay mounted once there is a session; the one not shown is `hidden`. So an
 * answer or a fit check keeps streaming while the visitor looks at the other tab. Unmounting a
 * panel would abort its request, and a fit check stopped halfway still counts toward the day's
 * limit.
 *
 * The page has two layers:
 * - {@link AskPage} keeps what must outlive a session: the session itself, the "session ended"
 *   note, the fit check with its draft (a running check must not stop when the visitor starts a
 *   new conversation), and the parts under the tabs ("How this works" stays open or folded);
 * - {@link AskPageContent} is rendered with `key={sessionId}`, so a new session starts it afresh.
 *   It owns the conversation and the message being typed, as the chat's hook expects, and works
 *   out Himmel's state from both tabs while rendering.
 */
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useLang } from '../lang'
import { type FitCheckStream, useAskSession, useChatStream, useFitCheckStream } from '../hooks'
import type { AskSession } from '../schemas'
import { pageContainerCls } from '../styles.ts'
import { AiBadge } from '../components/EvaluationUI.tsx'
import { AskStatsPanel } from '../components/chatbot/AskStatsPanel.tsx'
import { HowItWorks } from '../components/chatbot/HowItWorks.tsx'
import { ChatPanel } from '../components/chatbot/ChatPanel.tsx'
import { FitCheckPanel } from '../components/chatbot/FitCheckPanel.tsx'
import { HimmelMascot } from '../components/chatbot/HimmelMascot.tsx'
import { Tabs } from '../components/Tabs.tsx'
import { mascotStateOf, type TabActivity } from '../components/chatbot/mascotState.ts'
import { TurnstileGate } from '../components/chatbot/TurnstileGate.tsx'

/** The tabs, in order. The URL keeps the open one as `?tab=chat|fit`, so a link can open either. */
const TABS = ['chat', 'fit'] as const
type AskTab = (typeof TABS)[number]

export function AskPage() {
  const { session, create, clear } = useAskSession()
  const [sessionEnded, setSessionEnded] = useState(false)
  const fit = useFitCheckStream(session?.sessionId ?? null)
  const [fitDraft, setFitDraft] = useState(fit.jobDescription)

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
      <AskPageContent
        key={session?.sessionId ?? 'no-session'}
        session={session}
        sessionEnded={sessionEnded}
        onToken={startSession}
        onSessionExpired={endSession}
        onNewConversation={clear}
        fit={fit}
        fitDraft={fitDraft}
        onFitDraftChange={setFitDraft}
      />
      <div className="mt-8 flex flex-col gap-4">
        <AskStatsPanel />
        <HowItWorks />
      </div>
    </div>
  )
}

interface AskPageContentProps {
  /** The current session, or null before the human check. Also the component's `key`. */
  session: AskSession | null
  /** True after the backend ended the last session: the check then says so. */
  sessionEnded: boolean
  /** Called with the Turnstile token: the page creates the session. */
  onToken: (token: string) => Promise<void>
  /** Called when the backend says the session has expired. */
  onSessionExpired: () => void
  /** Called for "Start a new conversation" after the conversation limit. */
  onNewConversation: () => void
  /** The fit check ({@link FitCheckStream}) and its draft, kept by {@link AskPage}. */
  fit: FitCheckStream
  fitDraft: string
  onFitDraftChange: (draft: string) => void
}

/** The page for one session (or none yet): Himmel, the tabs and their panels. */
function AskPageContent({
  session,
  sessionEnded,
  onToken,
  onSessionExpired,
  onNewConversation,
  fit,
  fitDraft,
  onFitDraftChange,
}: AskPageContentProps) {
  const { L } = useLang()
  const [searchParams, setSearchParams] = useSearchParams()
  const tab: AskTab = searchParams.get('tab') === 'fit' ? 'fit' : 'chat'
  const chat = useChatStream(session?.sessionId ?? null)
  const [chatDraft, setChatDraft] = useState('')

  const selectTab = (next: AskTab) => setSearchParams({ tab: next }, { replace: true })

  const chatActivity: TabActivity = {
    status: chat.status,
    errorCode: chat.error?.code ?? null,
    typing: chatDraft.trim() !== '',
    answered: chat.messages.at(-1)?.role === 'assistant',
  }
  // The job description stays in its field after a check, so only a changed text is typing.
  const fitText = fitDraft.trim()
  const fitActivity: TabActivity = {
    status: fit.status,
    errorCode: fit.error?.code ?? null,
    typing: fitText !== '' && fitText !== fit.jobDescription,
    answered: fit.result !== null,
  }
  const mascotState =
    tab === 'chat'
      ? mascotStateOf(chatActivity, fitActivity)
      : mascotStateOf(fitActivity, chatActivity)

  return (
    <>
      <header className="flex flex-col items-center gap-2 mb-6 text-center">
        <HimmelMascot state={mascotState} className="w-24 sm:w-28" />
        <div className="flex items-center gap-2">
          <AiBadge />
          <h1 className="text-2xl font-extrabold text-text-primary m-0">{L.askTitle}</h1>
        </div>
        <p className="m-0 text-[0.92rem] text-text-sub leading-[1.55] max-w-[520px]">
          {L.askIntro}
        </p>
      </header>

      <Tabs
        tabs={TABS}
        labels={{ chat: L.askTabChat, fit: L.askTabFit }}
        selected={tab}
        onSelect={selectTab}
        idPrefix="ask"
        label={L.askTitle}
        allPanels={session !== null}
      />

      {session === null ? (
        <div role="tabpanel" id={`ask-panel-${tab}`} aria-labelledby={`ask-tab-${tab}`}>
          <TurnstileGate
            onToken={onToken}
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
              session={session}
              chat={chat}
              draft={chatDraft}
              onDraftChange={setChatDraft}
              onSessionExpired={onSessionExpired}
              onNewConversation={onNewConversation}
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
              onDraftChange={onFitDraftChange}
              onSessionExpired={onSessionExpired}
            />
          </div>
        </>
      )}
    </>
  )
}
