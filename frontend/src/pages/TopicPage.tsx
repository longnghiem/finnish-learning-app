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
import {pageTitleCls} from "../styles.ts";
import { SentenceEvaluationPanel } from '../components/SentenceEvaluationPanel.tsx'

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
  const topicName = topics?.find((t) => t.id === numericTopicId)?.name ?? `Topic ${topicId}`

  const [searchType, setSearchType] = useState<SearchType>('VERB')
  const [searchTerm, setSearchTerm] = useState('')
  const [debouncedSearchTerm, setDebouncedSearchTerm] = useState('')

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearchTerm(searchTerm), 300)
    return () => clearTimeout(timer)
  }, [searchTerm])

  const {
    data: cards,
    isLoading,
    isError,
  } = useCards(
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

  const [flipped, setFlipped] = useState(false)
  const [prevCardId, setPrevCardId] = useState<number | undefined>(currentCard?.id)
  if (prevCardId !== currentCard?.id) {
    setPrevCardId(currentCard?.id)
    setFlipped(false)
  }

  const [modalMode, setModalMode] = useState<'create' | 'edit' | null>(null)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)

  const deleteCard = useDeleteCard()

  // Reserve the right column whenever the eval panel could appear (logged-in
  // user + a current card). The flashcard column then stays anchored across
  // flips — the panel just fades in/out inside its pre-allocated slot.
  const slotReserved = isLoggedIn && !!currentCard
  const panelVisible = slotReserved && flipped

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
    <div className="flex justify-center items-start gap-9 flex-wrap">
      {/* Left column: flashcard + nav + dots. Stays anchored across flips. */}
      <div className="flex flex-col items-center gap-5 flex-none">
        {currentCard && (
          <Flashcard
            key={currentCard.id}
            card={currentCard}
            flipped={flipped}
            onFlip={() => setFlipped((f) => !f)}
          />
        )}
        <div className="flex items-center gap-4">
          <button
            className={navBtnClasses(!canGoPrev)}
            disabled={!canGoPrev}
            onClick={() => setCurrentIndex((i) => i - 1)}
          >
            {L.prev}
          </button>
          <span className="text-[0.8rem] text-text-muted font-semibold min-w-[52px] text-center">
            {L.cardOf(currentIndex + 1, total)}
          </span>
          <button
            className={navBtnClasses(!canGoNext)}
            disabled={!canGoNext}
            onClick={() => setCurrentIndex((i) => i + 1)}
          >
            {L.next}
          </button>
        </div>
        <ProgressBar total={total} current={currentIndex} />
      </div>

      {/* Right column: reserved slot. Fades the eval panel in/out without
          moving the card column. */}
      {slotReserved && (
        <div className="flex-[1_1_380px] max-w-[520px] min-w-[320px] self-start">
          <div
            className="transition-opacity duration-200"
            style={{
              opacity: panelVisible ? 1 : 0,
              pointerEvents: panelVisible ? 'auto' : 'none',
            }}
          >
            {currentCard && (
              <SentenceEvaluationPanel
                key={`eval-${currentCard.id}`}
                word={currentCard.name}
                meaning={currentCard.translation}
                autoFocus={panelVisible}
              />
            )}
          </div>
        </div>
      )}
    </div>
  )

  return (
    <div className={`page-enter mx-auto px-6 pt-7 pb-12 ${slotReserved ? 'max-w-[1080px]' : 'max-w-170'}`}>
      <button
        onClick={() => navigate('/')}
        className="bg-transparent border-none cursor-pointer text-text-muted text-sm font-[inherit] mb-4 flex items-center gap-1 p-0 font-semibold"
      >
        {L.allTopics}
      </button>

      <h1 className={pageTitleCls}>
        {topicName}
      </h1>

      <div className="mb-7 max-w-170 mx-auto">
        <SearchBar
          searchType={searchType}
          searchTerm={searchTerm}
          onSearchTypeChange={(t) => {
            setSearchType(t)
            setSearchTerm('')
          }}
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
                setCurrentIndex((prev) => Math.max(0, prev - 1))
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