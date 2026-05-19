import type { CardResponse } from '../types'
import { getImageUrl } from '../api'
import { useLang } from '../lang'
import {
  backCardWrapperCls,
  cardExampleCls,
  cardImgCls,
  cardNameCls,
  cardOverlayCls,
  cardTranslationCls,
  cardWrapperCls,
  flipPromptCls,
  frontCardWrapperCls,
} from '../styles.ts'

interface FlashcardProps {
  card: CardResponse
  /** True when the card is currently showing its back side */
  flipped: boolean
  onFlip: () => void
}

/**
 * Topic-page flashcard.
 *
 * The parent owns `flipped` (matches `QuizFlashcard`'s API) so it can:
 * - reset the flip state when navigating between cards;
 * - react to flip state (e.g. mount the `SentenceEvaluationPanel` only when flipped).
 */
export function Flashcard({ card, flipped, onFlip }: FlashcardProps) {
  const { L } = useLang()
  const imageUrl = getImageUrl(card.imageUrl)

  return (
    <div className={cardWrapperCls} onClick={onFlip}>
      <div className={`flashcard-inner${flipped ? ' flipped' : ''}`}>
        {/* Front */}
        <div className={frontCardWrapperCls}>
          <img src={imageUrl} alt={card.translation} className={cardImgCls} />
          <div className={cardOverlayCls}>
            <p className={cardTranslationCls}>{card.translation}</p>
            <p className={flipPromptCls}>{L.tapToFlip}</p>
          </div>
        </div>

        {/* Back */}
        <div className={backCardWrapperCls}>
          <img src={imageUrl} alt={card.name} className={cardImgCls} />
          <div className={cardOverlayCls}>
            <p className={cardNameCls}>{card.name}</p>
            <p className={cardExampleCls}>{card.exampleSentence}</p>
          </div>
        </div>
      </div>
    </div>
  )
}