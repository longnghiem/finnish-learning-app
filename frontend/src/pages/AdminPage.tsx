import { useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/useAuth.ts'
import { useLang } from '../lang'
import { Tabs } from '../components/Tabs.tsx'
import { CardsAdminPanel } from '../components/admin/CardsAdminPanel.tsx'
import { ChatbotAdminPanel } from '../components/admin/ChatbotAdminPanel.tsx'

/** The tabs, in order. The URL keeps the open one as `?tab=cards|chatbot`. */
const TABS = ['cards', 'chatbot'] as const
type AdminTab = (typeof TABS)[number]

/**
 * The admin page (`/admin`), for users with the `ADMIN` role: the tabs Cards (the flash cards)
 * and Chatbot (what the recruiter chatbot stored).
 *
 * Anyone else is sent to the login page, and nothing is rendered meanwhile, so no admin request
 * is made for them. The backend refuses those requests anyway (403); the check here only spares
 * the useless calls.
 *
 * Only the open tab is rendered: the other one makes no requests.
 */
export function AdminPage() {
  const { isAdmin } = useAuth()
  const navigate = useNavigate()
  const { L } = useLang()
  const [searchParams, setSearchParams] = useSearchParams()
  const tab: AdminTab = searchParams.get('tab') === 'chatbot' ? 'chatbot' : 'cards'

  useEffect(() => {
    if (!isAdmin) navigate('/login', { replace: true })
  }, [isAdmin, navigate])

  if (!isAdmin) return null

  return (
    <div className="page-enter max-w-275 mx-auto px-6 pt-10 pb-16">
      <h1 className="text-2xl font-extrabold text-text-primary m-0 mb-5">{L.adminTitle}</h1>
      <div className="max-w-md">
        <Tabs
          tabs={TABS}
          labels={{ cards: L.adminTabCards, chatbot: L.adminTabChatbot }}
          selected={tab}
          onSelect={(next) => setSearchParams({ tab: next }, { replace: true })}
          idPrefix="admin"
          label={L.adminTitle}
          allPanels={false}
        />
      </div>
      <div role="tabpanel" id={`admin-panel-${tab}`} aria-labelledby={`admin-tab-${tab}`}>
        {tab === 'cards' ? <CardsAdminPanel /> : <ChatbotAdminPanel />}
      </div>
    </div>
  )
}
