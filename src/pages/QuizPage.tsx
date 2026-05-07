import {backNavigationBtnCls, pageContainerCls, pageTitleCls} from "../styles.ts";
import {useNavigate, useParams} from "react-router-dom";
import {useAuth} from "../auth/useAuth.ts";
import {useLang} from "../lang";
import {useEffect, useState} from "react";
import {useTopics} from "../hooks";
import {useQuizCards, useSubmitAnswer} from "../hooks/useQuiz.ts";
import {type QuizAnswerLog, QuizSummary} from "../components/QuizSummary.tsx";
import type {QuizCard} from "../api/quiz.ts";
import {QuizFlashcard} from "../components/QuizFlashcard.tsx";
import {GradeButtons} from "../components/GradeButton.tsx";
import {ProgressBar} from "../components/ProgressBar.tsx";

/**
 * Quiz page: drives the user through a batch of due / new cards
 * one at a time, recording grades and showing a summary at the end.
 *
 *   reviewing ──answer success, more left──► reviewing (next index)
 *             ──answer success, last card──► summary
 *
 *   summary  ──quiz again──► loading (refetch)
 *            ──back to topic──► /topics/:topicId
 */
export function QuizPage() {
  const { topicId: topicIdParam } = useParams<{ topicId: string }>()
  const topicId = Number(topicIdParam)
  const navigate = useNavigate()
  const { L } = useLang()
  const { isLoggedIn } = useAuth()

  // Redirect unauthenticated users — quiz endpoints require auth.
  useEffect(() => {
    if (!isLoggedIn) navigate('/login', { replace: true })
  }, [isLoggedIn, navigate])

  const { data: topics } = useTopics()
  const topicName = topics?.find(t => t.id === topicId)?.name ?? `Topic ${topicId}`

  const {
    data: quizCards,
    isLoading,
    isError,
    refetch,
  } = useQuizCards(topicId, isLoggedIn && Number.isFinite(topicId))

  const submitAnswer = useSubmitAnswer()

  const [cardIndex, setCardIndex] = useState(0)
  const [flipped, setFlipped] = useState(false)
  const [answers, setAnswers] = useState<QuizAnswerLog[]>([])

  // Reset local progress whenever a fresh batch arrives (e.g. after "Quiz again").
  const [batchRef, setBatchRef] = useState<QuizCard[] | undefined>(quizCards as QuizCard[] | undefined)
  if (batchRef !== quizCards) {
    setBatchRef(quizCards as QuizCard[] | undefined)
    setCardIndex(0)
    setFlipped(false)
    setAnswers([])
  }

  const total = quizCards?.length ?? 0
  const currentCard = quizCards?.[cardIndex]
  const isSummary = total > 0 && cardIndex >= total

  const handleGrade = (quality: number) => {
    if (!currentCard) return
    submitAnswer.mutate(
      { cardId: currentCard.cardId, quality },
      {
        onSuccess: (res) => {
          setAnswers(prev => [...prev, { quality, correct: res.correct }])
          setCardIndex(i => i + 1)
          setFlipped(false)
        },
      },
    )
  }

  const handleQuizAgain = () => {
    setAnswers([])
    setCardIndex(0)
    setFlipped(false)
    void refetch()
  }

  return (
    <div className={pageContainerCls}>
      <button
        type="button"
        onClick={() => navigate(`/topics/${topicId}`)}
        className={backNavigationBtnCls}
      >
        {L.backToTopic}
      </button>

      <h1 className={pageTitleCls}>
        {L.quizTitle} — {topicName}
      </h1>

      {isLoading && (
        <div className="flex items-center justify-center py-20">
          <p className="text-[1.1rem] text-text-muted">Loading…</p>
        </div>
      )}

      {isError && (
        <div className="flex items-center justify-center py-20">
          <p className="text-[1.1rem] text-red">Failed to load quiz cards.</p>
        </div>
      )}

      {!isLoading && !isError && total === 0 && (
        <div className="flex flex-col items-center gap-4 py-20">
          <p className="text-[1.1rem] text-text-primary font-semibold">{L.noCardsDue}</p>
          <button
            type="button"
            onClick={() => navigate(`/topics/${topicId}`)}
            className="rounded-lg bg-amber text-nav-btn-text px-5 py-2.5 text-[0.85rem] font-extrabold border-none
              cursor-pointer font-[inherit] transition-colors duration-150 hover:bg-amber-hover"
          >
            {L.backToTopic}
          </button>
        </div>
      )}

      {!isLoading && !isError && total > 0 && !isSummary && currentCard && (
        <div className="flex flex-col items-center gap-6">
          <QuizFlashcard
            key={currentCard.cardId}
            card={currentCard}
            flipped={flipped}
            onFlip={() => setFlipped(f => !f)}
          />

          {flipped ? <GradeButtons onGrade={handleGrade} isLoading={submitAnswer.isPending} /> : <></>}

          <span className="text-[0.8rem] text-text-muted font-semibold">
            {L.cardOf(cardIndex + 1, total)}
          </span>
          <ProgressBar total={total} current={cardIndex} />
        </div>
      )}

      {isSummary && (
        <QuizSummary
          answers={answers}
          topicId={topicId}
          onQuizAgain={handleQuizAgain}
        />
      )}

    </div>
  )
}