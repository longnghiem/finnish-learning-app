import type { KeyboardEvent } from 'react'
import { tabActiveCls, tabCls, tabInactiveCls, tabListCls } from '../styles.ts'

/**
 * Tab buttons, as an ARIA tab list: Tab moves focus into the list and out of it, and the arrow
 * keys move between the tabs, as screen-reader users expect.
 *
 * Tab `t` gets the id `${idPrefix}-tab-${t}` and points at the panel `${idPrefix}-panel-${t}`;
 * the page renders that panel with `role="tabpanel"` and `aria-labelledby` set to the tab's id.
 *
 * @param tabs the tab ids, in order
 * @param labels the visible name of each tab
 * @param label the name of the whole tab list, read by screen readers
 * @param allPanels true if every tab's panel is in the page (hidden ones too); false if only the
 *   selected tab's panel is, so only that tab points at one
 */
export function Tabs<T extends string>({
  tabs,
  labels,
  selected,
  onSelect,
  idPrefix,
  label,
  allPanels,
}: {
  tabs: readonly T[]
  labels: Record<T, string>
  selected: T
  onSelect: (tab: T) => void
  idPrefix: string
  label: string
  allPanels: boolean
}) {
  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
    const step = event.key === 'ArrowRight' ? 1 : -1
    const next = tabs[(tabs.indexOf(selected) + step + tabs.length) % tabs.length]
    onSelect(next)
    document.getElementById(`${idPrefix}-tab-${next}`)?.focus()
  }

  return (
    <div role="tablist" aria-label={label} onKeyDown={handleKeyDown} className={tabListCls}>
      {tabs.map((tab) => {
        const active = tab === selected
        return (
          <button
            key={tab}
            id={`${idPrefix}-tab-${tab}`}
            type="button"
            role="tab"
            aria-selected={active}
            aria-controls={allPanels || active ? `${idPrefix}-panel-${tab}` : undefined}
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
