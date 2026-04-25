import type {CardResponse} from "../types";
import {getImageUrl} from "../api";
import { Flashcard as QuizletFlashcard } from "react-quizlet-flashcard";

interface FlashcardProps {
    card: CardResponse;
}

/**
 * A flip-animated flashcard displaying a Finnish vocabulary card.
 *
 * - **Front face**: Card image + English translation.
 * - **Back face**: Card image + Finnish word + example sentence.
 *
 * Uses `react-quizlet-flashcard` for the flip animation. The parent
 * should set `key={card.id}` on this component so that navigating to
 * a different card forces a remount and resets the flip state to front.
 *
 */
export function Flashcard({card}: FlashcardProps) {
    const imageUrl = getImageUrl(card.imageUrl);

    return (
        <div className="mx-auto w-full max-w-md">
            <QuizletFlashcard
                front={{
                    html: (
                        <div className="flex h-full flex-col items-center justify-center gap-4 p-6">
                            <img
                                src={imageUrl}
                                alt={card.translation}
                                className="max-h-48 rounded-lg object-contain"
                            />
                            <p className="text-xl font-semibold text-gray-800">
                                {card.translation}
                            </p>
                        </div>
                    ),
                }}
                back={{
                    html: (
                        <div className="flex h-full flex-col items-center justify-center gap-4 p-6">
                            <img
                                src={imageUrl}
                                alt={card.name}
                                className="max-h-48 rounded-lg object-contain"
                            />
                            <p className="text-xl font-bold text-blue-700">{card.name}</p>
                            <p className="text-center text-sm italic text-gray-600">
                                {card.exampleSentence}
                            </p>
                        </div>
                    ),
                }}
                className="rounded-xl shadow-lg"
                style={{ width: "100%", height: "350px" }}
            />
        </div>
    )
}