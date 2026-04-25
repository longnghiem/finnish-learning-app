import {useEffect} from "react";

interface ConfirmModalProps {
    /** The message to display in the modal body. */
    message: string;
    /** Called when the user confirms the action. */
    onConfirm: () => void;
    /** Called when the user cancels (or dismisses) the modal. */
    onCancel: () => void;
    /** When true, the confirm button shows a loading state and is disabled. */
    isLoading?: boolean;
}

/**
 * A generic confirmation modal with Cancel and Confirm buttons.
 *
 * Reusable for any destructive or important action — not specific to
 * card deletion. Displays a message and two action buttons. The confirm
 * button shows a loading indicator when `isLoading` is true.
 *
 * Dismissible via the Escape key or clicking the backdrop.
 *
 */
export function ConfirmModal({
 message,
 onConfirm,
 onCancel,
 isLoading = false,
}: ConfirmModalProps) {
    // --- Escape key ---
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key === "Escape") onCancel();
        };
        document.addEventListener("keydown", handleKeyDown);
        return () => document.removeEventListener("keydown", handleKeyDown);
    }, [onCancel]);

    // --- Prevent background scrolling ---
    useEffect(() => {
        document.body.style.overflow = "hidden";
        return () => {
            document.body.style.overflow = "";
        };
    }, []);

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
            onClick={onCancel}
        >
            <div
                className="mx-4 w-full max-w-sm rounded-xl bg-white p-6 shadow-xl"
                onClick={(e) => e.stopPropagation()}
            >
                <p className="mb-6 text-sm text-gray-700">{message}</p>

                <div className="flex justify-end gap-3">
                    <button
                        type="button"
                        onClick={onCancel}
                        disabled={isLoading}
                        className="rounded-md bg-gray-200 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-300 disabled:opacity-50"
                    >
                        Cancel
                    </button>
                    <button
                        type="button"
                        onClick={onConfirm}
                        disabled={isLoading}
                        className="rounded-md bg-red-500 px-4 py-2 text-sm font-medium text-white hover:bg-red-600 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                        {isLoading ? "Deleting..." : "Delete"}
                    </button>
                </div>
            </div>
        </div>
    );
}