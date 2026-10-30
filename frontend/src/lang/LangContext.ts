import { createContext, useContext } from 'react'
import { I18N, type I18nStrings, type Lang } from './translations'

interface LangContextType {
  lang: Lang
  L: I18nStrings
  toggleLang: () => void
}

export const LangContext = createContext<LangContextType>({ lang: 'en', L: I18N.en, toggleLang: () => {} })

export const useLang = () => useContext(LangContext)
