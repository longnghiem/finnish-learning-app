import type {SearchType} from '../types'
import {useLang} from '../lang'

interface SearchBarProps {
  searchType: SearchType
  searchTerm: string
  onSearchTypeChange: (type: SearchType) => void
  onSearchTermChange: (term: string) => void
}

const toggleClassNames = (active: boolean) =>
  `rounded-lg px-4 py-[7px] text-[0.8rem] font-bold border-none cursor-pointer font-[inherit] transition-colors duration-150 ${
    active ? 'bg-amber text-nav-btn-text' : 'bg-surface-alt text-text-sub'
  }`

export function SearchBar({ searchType, searchTerm, onSearchTypeChange, onSearchTermChange }: SearchBarProps) {
  const { L } = useLang()

  return (
    <div className="flex items-center gap-2.5">
      <div className="flex gap-1 shrink-0">
        <button
          type="button"
          className={toggleClassNames(searchType === 'VERB')}
          onClick={() => onSearchTypeChange('VERB')}
        >
          {L.word}
        </button>
        <button
          type="button"
          className={toggleClassNames(searchType === 'SENTENCE')}
          onClick={() => onSearchTypeChange('SENTENCE')}
        >
          {L.sentence}
        </button>
      </div>
      <input
        type="text"
        value={searchTerm}
        onChange={e => onSearchTermChange(e.target.value)}
        placeholder={searchType === 'VERB' ? L.searchByWord : L.searchBySentence}
        className="flex-1 rounded-lg border border-border-input bg-surface text-text-primary px-3.5 py-2 text-sm font-[inherit]
         outline-none transition-[border-color,box-shadow] duration-150 focus:border-accent focus:ring-2 focus:ring-accent/20"
      />
    </div>
  )
}