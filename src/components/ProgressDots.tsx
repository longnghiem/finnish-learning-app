interface ProgressDotsProps {
    /** Total number of items. */
    total: number;
    /** Currently active item (0-indexed). */
    current: number;
}

/**
 * A row of small dots indicating progress through a list of items.
 * The dot at position `current` is highlighted; all others are muted.
 */
export function ProgressDots({ total, current }: ProgressDotsProps) {
    return (
        <div className="flex items-center justify-center gap-2">
            {Array.from({ length: total }, (_, i) => (
                <span
                    key={i}
                    className={`inline-block h-2.5 w-2.5 rounded-full transition-colors ${
                        i === current ? "bg-blue-500" : "bg-gray-300"
                    }`}
                />
            ))}
        </div>
    );
}