import type {CardResponse} from "../types";
import {useCreateCard, useUpdateCard} from "../hooks";
import {useEffect, useMemo, useRef, useState} from "react";
import {getImageUrl} from "../api";
import {createCardSchema, editCardSchema} from "../schemas";

interface CardModalProps {
    /** Whether the modal is for creating a new card or editing an existing one. */
    mode: "create" | "edit";
    /** The topic ID to associate with a new card. */
    topicId: number;
    /** The card to edit (only provided in edit mode). */
    card?: CardResponse;
    /** Callback to close the modal. */
    onClose: () => void;
}

/**
 * A modal dialog for creating or editing a flashcard.
 *
 * - **Create mode**: All fields start empty; image is required.
 * - **Edit mode**: Text fields are pre-filled from `card`; the existing
 *   image is shown as a preview. A new image is only sent if the user
 *   selects one.
 *
 * Uses Zod schemas (`createCardSchema` / `editCardSchema`) for validation.
 * Submission delegates to `useCreateCard` or `useUpdateCard` hooks, which
 * handle `FormData` construction and cache invalidation.
 *
 * Dismissible via the Escape key, the backdrop click, or the Cancel / X buttons.
 */
export function CardModal({ mode, topicId, card, onClose }: CardModalProps) {
    const createCard = useCreateCard();
    const updateCard = useUpdateCard()

    const [name, setName] = useState(card?.name ?? "");
    const [exampleSentence, setExampleSentence] = useState(
        card?.exampleSentence ?? "",
    );
    const [translation, setTranslation] = useState(card?.translation ?? "");
    const [imageFile, setImageFile] = useState<File | null>(null);

    const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
    const [submitError, setSubmitError] = useState<string | null>(null);

    const fileInputRef = useRef<HTMLInputElement>(null);

    // --- Image preview (derived, not state) ---
    const previewUrl = useMemo(() => {
        if (imageFile) return URL.createObjectURL(imageFile);
        return card ? getImageUrl(card.imageUrl) : null;
    }, [imageFile, card]);

    // Cleanup blob URL when imageFile changes or component unmounts
    useEffect(() => {
        if (!imageFile || !previewUrl) return;
        return () => URL.revokeObjectURL(previewUrl);
    }, [imageFile, previewUrl]);

    // --- Escape key ---
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key === "Escape") onClose();
        };
        document.addEventListener("keydown", handleKeyDown);
        return () => document.removeEventListener("keydown", handleKeyDown);
    }, [onClose]);

    // --- Prevent background scrolling ---
    useEffect(() => {
        document.body.style.overflow = "hidden";
        return () => {
            document.body.style.overflow = "";
        };
    }, []);

    const isPending = createCard.isPending || updateCard.isPending;

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setFieldErrors({});
        setSubmitError(null);

        if (mode === "create") {
            const result = createCardSchema.safeParse({
                name,
                exampleSentence,
                translation,
                image: imageFile,
            });

            if (!result.success) {
                const errors: Record<string, string> = {};
                for (const issue of result.error.issues) {
                    const key = String(issue.path[0]);
                    if (!errors[key]) errors[key] = issue.message;
                }
                setFieldErrors(errors);
                return;
            }

            try {
                await createCard.mutateAsync({
                    name: result.data.name,
                    exampleSentence: result.data.exampleSentence,
                    translation: result.data.translation,
                    topicId,
                    image: result.data.image,
                });
                onClose();
            } catch (err) {
                setSubmitError(err instanceof Error ? err.message : "Failed to create card.");
            }
        } else {
            const result = editCardSchema.safeParse({
                name,
                exampleSentence,
                translation,
                image: imageFile ?? undefined,
            });

            if (!result.success) {
                const errors: Record<string, string> = {};
                for (const issue of result.error.issues) {
                    const key = String(issue.path[0]);
                    if (!errors[key]) errors[key] = issue.message;
                }
                setFieldErrors(errors);
                return;
            }

            try {
                await updateCard.mutateAsync({
                    id: card!.id,
                    data: {
                        name: result.data.name,
                        exampleSentence: result.data.exampleSentence,
                        translation: result.data.translation,
                        image: result.data.image,
                    },
                });
                onClose();
            } catch (err) {
                setSubmitError(err instanceof Error ? err.message : "Failed to update card.");
            }
        }
    };

    const inputClass =
        "w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500";
    const labelClass = "mb-1 block text-sm font-medium text-gray-700";
    const errorClass = "mt-1 text-xs text-red-500";

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
            onClick={onClose}
        >
            <div
                className="relative mx-4 w-full max-w-md rounded-xl bg-white p-6 shadow-xl"
                onClick={(e) => e.stopPropagation()}
            >
                {/* Close button */}
                <button
                    type="button"
                    onClick={onClose}
                    className="absolute right-3 top-3 text-gray-400 hover:text-gray-600"
                    aria-label="Close"
                >
                    ✕
                </button>

                <h2 className="mb-5 text-lg font-bold text-gray-800">
                    {mode === "create" ? "Create Card" : "Edit Card"}
                </h2>

                <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                    {/* Name */}
                    <div>
                        <label htmlFor="card-name" className={labelClass}>
                            Finnish Word
                        </label>
                        <input
                            id="card-name"
                            type="text"
                            value={name}
                            onChange={(e) => setName(e.target.value)}
                            className={inputClass}
                            placeholder="e.g. syödä"
                        />
                        {fieldErrors.name && (
                            <p className={errorClass}>{fieldErrors.name}</p>
                        )}
                    </div>

                    {/* Example Sentence */}
                    <div>
                        <label htmlFor="card-sentence" className={labelClass}>
                            Example Sentence
                        </label>
                        <textarea
                            id="card-sentence"
                            value={exampleSentence}
                            onChange={(e) => setExampleSentence(e.target.value)}
                            className={`${inputClass} resize-none`}
                            rows={3}
                            placeholder="e.g. Minä syön aamiaista"
                        />
                        {fieldErrors.exampleSentence && (
                            <p className={errorClass}>{fieldErrors.exampleSentence}</p>
                        )}
                    </div>

                    {/* Translation */}
                    <div>
                        <label htmlFor="card-translation" className={labelClass}>
                            English Translation
                        </label>
                        <input
                            id="card-translation"
                            type="text"
                            value={translation}
                            onChange={(e) => setTranslation(e.target.value)}
                            className={inputClass}
                            placeholder="e.g. to eat"
                        />
                        {fieldErrors.translation && (
                            <p className={errorClass}>{fieldErrors.translation}</p>
                        )}
                    </div>

                    {/* Image */}
                    <div>
                        <label htmlFor="card-image" className={labelClass}>
                            Image{mode === "edit" ? " (optional)" : ""}
                        </label>
                        <input
                            id="card-image"
                            ref={fileInputRef}
                            type="file"
                            accept="image/jpeg,image/png,image/gif,image/webp"
                            onChange={(e) => setImageFile(e.target.files?.[0] ?? null)}
                            className="block w-full text-sm text-gray-500 file:mr-3 file:rounded-md file:border-0 file:bg-blue-50 file:px-4 file:py-2 file:text-sm file:font-medium file:text-blue-700 hover:file:bg-blue-100"
                        />
                        {fieldErrors.image && (
                            <p className={errorClass}>{fieldErrors.image}</p>
                        )}
                        {previewUrl && (
                            <img
                                src={previewUrl}
                                alt="Preview"
                                className="mt-2 max-h-40 rounded-lg object-contain"
                            />
                        )}
                    </div>

                    {/* Submit error */}
                    {submitError && (
                        <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-600">
                            {submitError}
                        </p>
                    )}

                    {/* Actions */}
                    <div className="flex justify-end gap-3 pt-2">
                        <button
                            type="button"
                            onClick={onClose}
                            className="rounded-md bg-gray-200 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-300"
                        >
                            Cancel
                        </button>
                        <button
                            type="submit"
                            disabled={isPending}
                            className="rounded-md bg-blue-500 px-4 py-2 text-sm font-medium text-white hover:bg-blue-600 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                            {isPending
                                ? "Saving..."
                                : mode === "create"
                                    ? "Create"
                                    : "Save"}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}