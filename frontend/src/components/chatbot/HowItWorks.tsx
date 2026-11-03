import { Link } from 'react-router-dom'
import { useLang } from '../../lang'
import { CHAT_MESSAGES_PER_DAY, FIT_CHECKS_PER_DAY } from '../../api'
import {
  bulletListCls,
  evalListItemCls,
  evalPanelCls,
  evalPanelIntroCls,
  evalPanelTitleCls,
  finePrintCls,
  textLinkCls,
} from '../../styles.ts'

/** The public repository; the same URL as the backend's systemd unit (`Documentation=`). */
const SOURCE_CODE_URL = 'https://github.com/longnghiem/finnish-learning-app'

/** The architecture decision record of the chatbot, on GitHub. */
const ADR_URL = `${SOURCE_CODE_URL}/blob/main/docs/adr/0002-recruiter-chatbot-mcp-architecture.md`

/**
 * "How this works", folded by default: the path of a question from the browser to the profile,
 * the safety layers, and links to the source code and the architecture decision.
 *
 * - The diagram is an ordered list of boxes with arrows between them, not a picture. So it wraps
 *   on a phone like any text, follows the theme through the usual colour classes, and a screen
 *   reader reads it as a numbered list. The arrows are hidden from screen readers. A step marked
 *   `twoWay` gets a two-way arrow: the model asks the MCP server, and the entries come back.
 * - `<details>` does the folding: the browser handles the click, the keyboard and the open state,
 *   so the component needs none of its own.
 * - The limits come from {@link CHAT_MESSAGES_PER_DAY} and {@link FIT_CHECKS_PER_DAY}. The
 *   spending cap has no amount: it is a server setting and would go out of date here.
 */
export function HowItWorks() {
  const { L } = useLang()

  return (
    <section aria-labelledby="ask-how-title" className={evalPanelCls}>
      <details>
        <summary className="cursor-pointer">
          <h2 id="ask-how-title" className={`${evalPanelTitleCls} inline`}>
            {L.howTitle}
          </h2>
        </summary>

        <div className="flex flex-col gap-[13px] mt-[13px]">
          <p className={evalPanelIntroCls}>{L.howIntro}</p>

          <ol className="m-0 p-0 list-none flex flex-col">
            {L.howSteps.map((step, i) => (
              <li key={step.title} className="flex flex-col">
                {i > 0 && (
                  <span aria-hidden="true" className="text-center text-text-muted leading-6">
                    {step.twoWay ? '⇅' : '↓'}
                  </span>
                )}
                <div className={evalListItemCls}>
                  <span className="text-[0.88rem] font-bold text-text-primary">{step.title}</span>
                  <span className={evalPanelIntroCls}>{step.text}</span>
                </div>
              </li>
            ))}
          </ol>

          <h3 className={evalPanelTitleCls}>{L.howSafetyTitle}</h3>
          <ul className={bulletListCls}>
            {L.howSafety(CHAT_MESSAGES_PER_DAY, FIT_CHECKS_PER_DAY).map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>

          <p className={`${finePrintCls} flex flex-wrap gap-x-4 gap-y-1`}>
            <a
              href={SOURCE_CODE_URL}
              target="_blank"
              rel="noopener noreferrer"
              className={textLinkCls}
            >
              {L.howSourceCode}
            </a>
            <a href={ADR_URL} target="_blank" rel="noopener noreferrer" className={textLinkCls}>
              {L.howAdr}
            </a>
            <Link to="/privacy" className={textLinkCls}>
              {L.askPrivacyLink}
            </Link>
          </p>
        </div>
      </details>
    </section>
  )
}
