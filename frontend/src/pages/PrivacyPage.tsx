import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useLang } from '../lang'
import { CONTACT } from '../contact.ts'
import {
  bulletListCls,
  evalPanelCls,
  evalPanelIntroCls,
  evalPanelTitleCls,
  finePrintCls,
  pageContainerCls,
  pageTitleCls,
  textLinkCls,
} from '../styles.ts'

/**
 * The privacy page of the recruiter chatbot (`/privacy`), linked from the notices under the chat
 * and the fit check. Public, in English and Finnish.
 *
 * It says what the chatbot stores, why, for how long, which other services see the data, and how
 * to ask for deletion. The texts must stay true to the backend: the 30 days are its retention
 * setting, and "a keyed hash of your IP address" is what it stores instead of the address.
 */
export function PrivacyPage() {
  const { L } = useLang()

  return (
    <div className={pageContainerCls}>
      <h1 className={pageTitleCls}>{L.privacyTitle}</h1>

      <article className={evalPanelCls}>
        <p className={evalPanelIntroCls}>{L.privacyIntro}</p>

        <PrivacySection title={L.privacyStoredTitle}>
          <ul className={bulletListCls}>
            {L.privacyStored.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
          <p className={evalPanelIntroCls}>{L.privacyHash}</p>
          <p className={evalPanelIntroCls}>{L.privacyLogs}</p>
        </PrivacySection>

        <PrivacySection title={L.privacyBrowserTitle}>
          <p className={evalPanelIntroCls}>{L.privacyBrowser}</p>
        </PrivacySection>

        <PrivacySection title={L.privacyWhyTitle}>
          <p className={evalPanelIntroCls}>{L.privacyWhy}</p>
        </PrivacySection>

        <PrivacySection title={L.privacyHowLongTitle}>
          <ul className={bulletListCls}>
            {L.privacyHowLong.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </PrivacySection>

        <PrivacySection title={L.privacyServicesTitle}>
          <ul className={bulletListCls}>
            {L.privacyServices.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </PrivacySection>

        <PrivacySection title={L.privacyDeleteTitle}>
          <p className={evalPanelIntroCls}>
            {L.privacyDelete}{' '}
            <a href={`mailto:${CONTACT.email}`} className={textLinkCls}>
              {CONTACT.email}
            </a>
            . {L.privacyDeleteHow}
          </p>
          <p className={evalPanelIntroCls}>{L.privacySensitive}</p>
        </PrivacySection>

        <div className="flex items-center justify-between gap-3 flex-wrap">
          <Link to="/ask" className={`${textLinkCls} text-[0.86rem]`}>
            ← {L.privacyBack}
          </Link>
          <p className={finePrintCls}>{L.privacyUpdated}</p>
        </div>
      </article>
    </div>
  )
}

/** One part of the page: a heading and its text. */
function PrivacySection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-2">
      <h2 className={evalPanelTitleCls}>{title}</h2>
      {children}
    </section>
  )
}
