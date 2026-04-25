import {useParams} from "react-router-dom";
import {useAuth} from "../auth/useAuth.ts";
import {useCards, useTopics} from "../hooks";
import {useEffect, useState} from "react";
import type {SearchType} from "../types";
import {ProgressDots} from "../components/ProgressDots.tsx";
import {SearchBar} from "../components/SearchBar.tsx";
import {Flashcard} from "../components/Flashcard.tsx";

export function TopicPage() {
    const {topicId} = useParams<{topicId: string}>()
    const numericTopicId = Number(topicId);

    const { isLoggedIn } = useAuth();
    const { data: topics } = useTopics();
    const topicName =
        topics?.find((t) => t.id === numericTopicId)?.name ?? `Topic ${topicId}`;

    // --- Search state ---
    const [searchType, setSearchType] = useState<SearchType>("VERB");
    const [searchTerm, setSearchTerm] = useState("");
    const [debouncedSearchTerm, setDebouncedSearchTerm] = useState("");

    useEffect(() => {
        const timer = setTimeout(() => setDebouncedSearchTerm(searchTerm), 300);
        return () => clearTimeout(timer);
    }, [searchTerm]);

    // --- Data fetching ---
    const {
        data: cards,
        isLoading,
        isError,
    } = useCards(
        numericTopicId,
        debouncedSearchTerm ? searchType : undefined,
        debouncedSearchTerm || undefined,
    );

    // --- Card navigation ---
    const [currentIndex, setCurrentIndex] = useState(0);
    const [prevCards, setPrevCards] = useState(cards);

    // Reset to first card when the card list changes (derived state pattern)
    if (prevCards !== cards) {
        setPrevCards(cards);
        setCurrentIndex(0);
    }

    const currentCard = cards?.[currentIndex];
    const total = cards?.length ?? 0;
    const canGoPrev = currentIndex > 0;
    const canGoNext = currentIndex < total - 1;

    // --- Loading / Error states ---
    if (isLoading) {
        return (
            <div className="flex items-center justify-center py-20">
                <p className="text-lg text-gray-500">Loading cards...</p>
            </div>
        );
    }

    if (isError) {
        return (
            <div className="flex items-center justify-center py-20">
                <p className="text-lg text-red-500">Failed to load cards.</p>
            </div>
        );
    }

    const flashcardArea = total === 0 ? (
        <div className="py-16 text-center">
            <p className="text-lg text-gray-500">
                No cards yet — create the first one!
            </p>
        </div>
    ) : (
        <div className="flex flex-col items-center gap-6">
            {/* Flashcard — key forces remount to reset flip state */}
            {currentCard && (
                <Flashcard key={currentCard.id} card={currentCard} />
            )}

            {/* Prev / Next navigation */}
            <div className="flex items-center gap-4">
                <button
                    type="button"
                    onClick={() => setCurrentIndex((i) => i - 1)}
                    disabled={!canGoPrev}
                    className="rounded-md bg-gray-200 px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-300 disabled:cursor-not-allowed disabled:opacity-40"
                >
                    ← Prev
                </button>
                <span className="text-sm text-gray-500">
                    {currentIndex + 1} / {total}
                </span>
                <button
                    type="button"
                    onClick={() => setCurrentIndex((i) => i + 1)}
                    disabled={!canGoNext}
                    className="rounded-md bg-gray-200 px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-300 disabled:cursor-not-allowed disabled:opacity-40"
                >
                    Next →
                </button>
            </div>

            {/* Progress dots */}
            <ProgressDots total={total} current={currentIndex} />
        </div>
    );

    return (
        <div className="mx-auto max-w-2xl px-6 py-10">
            {/* Topic heading */}
            <h1 className="mb-6 text-center text-2xl font-bold text-gray-800">
                {topicName}
            </h1>

            {/* Search bar */}
            <div className="mb-8">
                <SearchBar
                    searchType={searchType}
                    searchTerm={searchTerm}
                    onSearchTypeChange={setSearchType}
                    onSearchTermChange={setSearchTerm}
                />
            </div>

            {/* Flashcard area */}
            {flashcardArea}

            {/* Auth-gated mutation buttons */}
            {isLoggedIn && (
                <div className="mt-8 flex items-center justify-center gap-3">
                    <button
                        type="button"
                        onClick={() => console.log("open create modal")}
                        className="rounded-md bg-green-500 px-4 py-2 text-sm font-medium text-white hover:bg-green-600"
                    >
                        Create Card
                    </button>
                    {currentCard && (
                        <>
                            <button
                                type="button"
                                onClick={() => console.log("open edit modal")}
                                className="rounded-md bg-yellow-500 px-4 py-2 text-sm font-medium text-white hover:bg-yellow-600"
                            >
                                Edit
                            </button>
                            <button
                                type="button"
                                onClick={() => console.log("open delete modal")}
                                className="rounded-md bg-red-500 px-4 py-2 text-sm font-medium text-white hover:bg-red-600"
                            >
                                Delete
                            </button>
                        </>
                    )}
                </div>
            )}
        </div>
    );
}