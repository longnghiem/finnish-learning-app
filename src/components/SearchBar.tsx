import type {SearchType} from "../types";

interface SearchBarProps {
    /** The active search mode. */
    searchType: SearchType;
    /** The current search input value. */
    searchTerm: string;
    /** Called when the user switches between VERB and SENTENCE. */
    onSearchTypeChange: (type: SearchType) => void;
    /** Called on every keystroke in the search input. */
    onSearchTermChange: (term: string) => void;
}

/**
 * A controlled search bar with toggle buttons for search type.
 *
 * Provides two toggle buttons ("Word" for `VERB`, "Sentence" for `SENTENCE`)
 * and a text input. The active toggle is visually highlighted.
 *
 * Debouncing is **not** handled here — it is the parent's responsibility
 * to debounce before passing the term to the data-fetching hook.
 *
 */
export function SearchBar({
  searchType,
  searchTerm,
  onSearchTypeChange,
  onSearchTermChange,
}: SearchBarProps) {
    const baseBtn =
        "rounded-md px-4 py-2 text-sm font-medium transition-colors";
    const activeBtn = "bg-blue-500 text-white";
    const inactiveBtn = "bg-gray-200 text-gray-700 hover:bg-gray-300";

    return (
        <div className="flex items-center gap-3">
            <div className="flex gap-1">
                <button
                    type="button"
                    onClick={() => onSearchTypeChange("VERB")}
                    className={`${baseBtn} ${searchType === "VERB" ? activeBtn : inactiveBtn}`}
                >
                    Word
                </button>
                <button
                    type="button"
                    onClick={() => onSearchTypeChange("SENTENCE")}
                    className={`${baseBtn} ${searchType === "SENTENCE" ? activeBtn : inactiveBtn}`}
                >
                    Sentence
                </button>
            </div>
            <input
                type="text"
                value={searchTerm}
                onChange={(e) => onSearchTermChange(e.target.value)}
                placeholder={
                    searchType === "VERB" ? "Search by word..." : "Search by sentence..."
                }
                className="flex-1 rounded-md border border-gray-300 px-4 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
        </div>
    );
}