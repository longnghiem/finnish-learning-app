import {useNavigate, useParams} from 'react-router-dom'
import {useAuth} from '../auth/useAuth.ts'
import {useCards, useDeleteCard, useTopics} from '../hooks'
import {useEffect, useState} from 'react'
import type {SearchType} from '../types'
import {ProgressBar} from '../components/ProgressBar.tsx'
import {SearchBar} from '../components/SearchBar.tsx'
import {Flashcard} from '../components/Flashcard.tsx'
import {CardModal} from '../components/CardModal.tsx'
import {ConfirmModal} from '../components/ConfirmModal.tsx'
import {useLang} from '../lang'
import {pageContainerCls, pageTitleCls} from "../styles.ts";

const navBtnClasses = (disabled: boolean) =>
  `rounded-lg px-[18px] py-2 text-sm font-semibold font-[inherit] transition-colors duration-150 ${
    disabled
      ? 'bg-transparent text-text-muted border border-transparent cursor-not-allowed opacity-35'
      : 'bg-surface-alt text-text-primary border border-border cursor-pointer'
  }`

export function TopicPage() {
  const { topicId } = useParams<{ topicId: string }>()
  const numericTopicId = Number(topicId)
  const navigate = useNavigate()

  const { isLoggedIn, isAdmin } = useAuth()
  const { L } = useLang()
  const { data: topics } = useTopics()
  const topicName = topics?.find(t => t.id === numericTopicId)?.name ?? `Topic ${topicId}`

  const [searchType, setSearchType] = useState<SearchType>('VERB')
  const [searchTerm, setSearchTerm] = useState('')
  const [debouncedSearchTerm, setDebouncedSearchTerm] = useState('')

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearchTerm(searchTerm), 300)
    return () => clearTimeout(timer)
  }, [searchTerm])

  const { data: cards, isLoading, isError } = useCards(
    numericTopicId,
    debouncedSearchTerm ? searchType : undefined,
    debouncedSearchTerm || undefined,
  )

  const [currentIndex, setCurrentIndex] = useState(0)
  const [prevCards, setPrevCards] = useState(cards)

  if (prevCards !== cards) {
    setPrevCards(cards)
    setCurrentIndex(0)
  }

  const currentCard = cards?.[currentIndex]
  const total = cards?.length ?? 0
  const canGoPrev = currentIndex > 0
  const canGoNext = currentIndex < total - 1

  const [modalMode, setModalMode] = useState<'create' | 'edit' | null>(null)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)

  const deleteCard = useDeleteCard()

  const flashcardArea = isLoading ? (
    <div className="flex items-center justify-center py-20">
      <p className="text-[1.1rem] text-text-muted">Loading cards…</p>
    </div>
  ) : isError ? (
    <div className="flex items-center justify-center py-20">
      <p className="text-[1.1rem] text-red">Failed to load cards.</p>
    </div>
  ) : total === 0 ? (
    <div className="text-center py-[72px] text-text-muted text-base font-semibold">
      {L.noCardsFound}
    </div>
  ) : (
    <div className="flex flex-col items-center gap-6">
      {currentCard && <Flashcard key={currentCard.id} card={currentCard} />}
      <div className="flex items-center gap-4">
        <button className={navBtnClasses(!canGoPrev)} disabled={!canGoPrev} onClick={() => setCurrentIndex(i => i - 1)}>
          {L.prev}
        </button>
        <span className="text-[0.8rem] text-text-muted font-semibold min-w-[52px] text-center">
          {L.cardOf(currentIndex + 1, total)}
        </span>
        <button className={navBtnClasses(!canGoNext)} disabled={!canGoNext} onClick={() => setCurrentIndex(i => i + 1)}>
          {L.next}
        </button>
      </div>
      <ProgressBar total={total} current={currentIndex} />
    </div>
  )

  return (
    <div className={pageContainerCls}>
      <button
        onClick={() => navigate('/')}
        className="bg-transparent border-none cursor-pointer text-text-muted text-sm font-[inherit] mb-4 flex items-center gap-1 p-0 font-semibold"
      >
        {L.allTopics}
      </button>

      <h1 className={pageTitleCls}>
        {topicName}
      </h1>

      <div className="mb-7">
        <SearchBar
          searchType={searchType}
          searchTerm={searchTerm}
          onSearchTypeChange={t => { setSearchType(t); setSearchTerm('') }}
          onSearchTermChange={setSearchTerm}
        />
      </div>

      {flashcardArea}

      {isLoggedIn && total > 0 && (
        <div className="mt-7 flex justify-center">
          <button
            type="button"
            onClick={() => navigate(`/quiz/${numericTopicId}`)}
            className="rounded-lg bg-accent text-white px-6 py-3 text-[0.95rem] font-extrabold border-none cursor-pointer
              font-[inherit] transition-colors duration-150 hover:opacity-90"
          >
            {L.startQuiz}
          </button>
        </div>
      )}

      {isAdmin && (
        <div className="mt-9 flex items-center justify-center gap-2.5 flex-wrap">
          <button
            type="button"
            onClick={() => setModalMode('create')}
            className="rounded-lg bg-green text-white px-5 py-2.25 text-[0.85rem] font-bold border-none cursor-pointer
            font-[inherit] transition-colors duration-150 hover:bg-green-hover"
          >
            {L.createCard}
          </button>
          {currentCard && (
            <>
              <button
                type="button"
                onClick={() => setModalMode('edit')}
                className="rounded-lg bg-yellow text-white px-5 py-2.25 text-[0.85rem] font-bold border-none cursor-pointer
                font-[inherit] transition-colors duration-150 hover:bg-yellow-hover"
              >
                {L.edit}
              </button>
              <button
                type="button"
                onClick={() => setShowDeleteConfirm(true)}
                className="rounded-lg bg-red text-white px-5 py-2.25 text-[0.85rem] font-bold border-none cursor-pointer
                font-[inherit] transition-colors duration-150 hover:bg-red-hover"
              >
                {L.delete}
              </button>
            </>
          )}
        </div>
      )}

      {modalMode && (
        <CardModal
          mode={modalMode}
          topicId={numericTopicId}
          card={modalMode === 'edit' ? currentCard : undefined}
          onClose={() => setModalMode(null)}
        />
      )}

      {showDeleteConfirm && currentCard && (
        <ConfirmModal
          message={L.deleteConfirmMsg}
          onConfirm={() => {
            deleteCard.mutate(currentCard.id, {
              onSuccess: () => {
                setShowDeleteConfirm(false)
                setCurrentIndex(prev => Math.max(0, prev - 1))
              },
            })
          }}
          onCancel={() => setShowDeleteConfirm(false)}
          isLoading={deleteCard.isPending}
        />
      )}
    </div>
  )
}