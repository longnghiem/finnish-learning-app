import {useState} from 'react'
import type {CardResponse} from '../types'
import {getImageUrl} from '../api'
import {useLang} from '../lang'
import {
  backCardWrapperCls, cardExampleCls, cardImgCls, cardNameCls,
  cardOverlayCls,
  cardTranslationCls, cardWrapperCls,
  flipPromptCls,
  frontCardWrapperCls
} from "../styles.ts";

interface FlashcardProps {
  card: CardResponse
}

export function Flashcard({ card }: FlashcardProps) {
  const { L } = useLang()
  const [flipped, setFlipped] = useState(false)
  const [prevCardId, setPrevCardId] = useState(card.id)

  if (prevCardId !== card.id) {
    setPrevCardId(card.id)
    setFlipped(false)
  }

  const imageUrl = getImageUrl(card.imageUrl)

  return (
    <div
      className={cardWrapperCls}
      onClick={() => setFlipped(f => !f)}
    >
      <div className={`flashcard-inner${flipped ? ' flipped' : ''}`}>
        {/* Front */}
        <div className={frontCardWrapperCls}>
          <img
            src={imageUrl}
            alt={card.translation}
            className={cardImgCls}
          />
          <div className={cardOverlayCls}>
            <p className={cardTranslationCls}>
              {card.translation}
            </p>
            <p className={flipPromptCls}>
              {L.tapToFlip}
            </p>
          </div>
        </div>

        {/* Back */}
        <div className={backCardWrapperCls}>
          <img
            src={imageUrl}
            alt={card.name}
            className={cardImgCls}
          />
          <div className={cardOverlayCls}>
            <p className={cardNameCls}>
              {card.name}
            </p>
            <p className={cardExampleCls}>
              {card.exampleSentence}
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}