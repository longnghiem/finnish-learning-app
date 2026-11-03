import { useMemo, useState } from 'react'
import { useCards, useDeleteCard, useTopics } from '../hooks'
import { CardModal } from './CardModal.tsx'
import { ConfirmModal } from './ConfirmModal.tsx'
import { useLang } from '../lang'
import type { CardResponse } from '../types'
import { adminTableWrapCls, adminTdCls, adminThCls } from '../styles.ts'

/**
 * The "Cards" tab of the admin page: every flash card in a table, with a topic filter, and
 * buttons to create, edit and delete cards. Only an admin reaches it: the admin page checks the
 * role, and the backend allows card changes to `ROLE_ADMIN` only.
 */
export function CardsAdminPanel() {
  const { L } = useLang()

  const { data: topics } = useTopics()
  const { data: cards, isLoading, isError } = useCards()
  const deleteCard = useDeleteCard()

  const [filterTopic, setFilterTopic] = useState<'all' | string>('all')
  const [modalState, setModalState] = useState<{
    mode: 'create' | 'edit'
    topicId: number
    card?: CardResponse
  } | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<CardResponse | null>(null)

  const topicMap = useMemo(() => {
    const map: Record<number, string> = {}
    topics?.forEach((t) => {
      map[t.id] = t.name
    })
    return map
  }, [topics])

  const displayed = useMemo(() => {
    if (!cards) return []
    if (filterTopic === 'all') return cards
    return cards.filter((c) => c.topicId === Number(filterTopic))
  }, [cards, filterTopic])

  const defaultTopicId = filterTopic !== 'all' ? Number(filterTopic) : 4

  return (
    <>
      <div className="flex items-center justify-between mb-7 flex-wrap gap-3.5">
        <h2 className="text-xl font-extrabold text-text-primary m-0">{L.manageCards}</h2>
        <div className="flex gap-2.5 items-center flex-wrap">
          <select
            value={filterTopic}
            onChange={(e) => setFilterTopic(e.target.value)}
            className="rounded-lg border border-border-input bg-surface text-text-primary px-3 py-2 text-sm font-[inherit] outline-none cursor-pointer"
          >
            <option value="all">{L.allTopicsFilter}</option>
            {topics?.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
          <button
            onClick={() => setModalState({ mode: 'create', topicId: defaultTopicId })}
            className="rounded-lg bg-green text-white px-4.5 py-2 text-sm font-bold border-none cursor-pointer font-[inherit] transition-colors duration-150 hover:bg-green-hover"
          >
            + {L.createCard}
          </button>
        </div>
      </div>

      <div className={adminTableWrapCls}>
        {isLoading ? (
          <div className="p-16 text-center text-text-muted text-[0.9rem] font-semibold">
            Loading…
          </div>
        ) : isError ? (
          <div className="p-16 text-center text-red text-[0.9rem] font-semibold">
            Failed to load cards.
          </div>
        ) : displayed.length === 0 ? (
          <div className="p-16 text-center text-text-muted text-[0.9rem] font-semibold">
            {L.noCardsYet}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse">
              <thead>
                <tr>
                  <th className={adminThCls}>Finnish</th>
                  <th className={adminThCls}>English</th>
                  <th className={`${adminThCls} min-w-55`}>Example sentence</th>
                  <th className={`${adminThCls} min-w-35`}>Topic</th>
                  <th className={`${adminThCls} w-30 text-right`}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {displayed.map((c, i) => (
                  <tr key={c.id} className={i % 2 === 1 ? 'bg-surface-alt' : ''}>
                    <td className={`${adminTdCls} font-bold text-accent`}>{c.name}</td>
                    <td className={adminTdCls}>{c.translation}</td>
                    <td className={`${adminTdCls} italic text-text-sub leading-normal max-w-65`}>
                      {c.exampleSentence}
                    </td>
                    <td className={`${adminTdCls} text-[0.78rem] text-text-muted font-semibold`}>
                      {topicMap[c.topicId] ?? `Topic ${c.topicId}`}
                    </td>
                    <td className={`${adminTdCls} text-right`}>
                      <div className="flex gap-1.5 justify-end">
                        <button
                          onClick={() =>
                            setModalState({ mode: 'edit', topicId: c.topicId, card: c })
                          }
                          className="rounded-md bg-yellow text-white px-3 py-1.25 text-[0.78rem] font-bold border-none cursor-pointer font-[inherit]"
                        >
                          {L.edit}
                        </button>
                        <button
                          onClick={() => setDeleteTarget(c)}
                          className="rounded-md bg-red text-white px-3 py-1.25 text-[0.78rem] font-bold border-none cursor-pointer font-[inherit]"
                        >
                          {L.delete}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      <p className="mt-3 text-[0.78rem] text-text-muted font-medium">
        {displayed.length} card{displayed.length !== 1 ? 's' : ''}
      </p>

      {modalState && (
        <CardModal
          mode={modalState.mode}
          topicId={modalState.topicId}
          card={modalState.card}
          onClose={() => setModalState(null)}
        />
      )}
      {deleteTarget && (
        <ConfirmModal
          message={L.deleteConfirmMsg}
          onConfirm={() => {
            deleteCard.mutate(deleteTarget.id, {
              onSuccess: () => setDeleteTarget(null),
            })
          }}
          onCancel={() => setDeleteTarget(null)}
          isLoading={deleteCard.isPending}
        />
      )}
    </>
  )
}
