import { useState, type ReactNode } from 'react'
import { LangContext } from './LangContext'
import { I18N, type Lang } from './translations'

export function LangProvider({ children }: { children: ReactNode }) {
  const [lang, setLang] = useState<Lang>(
    () => (localStorage.getItem('lang') as Lang) || 'en',
  )
  const L = I18N[lang]

  const toggleLang = () => {
    setLang(prev => {
      const next: Lang = prev === 'en' ? 'fi' : 'en'
      localStorage.setItem('lang', next)
      return next
    })
  }

  return (
    <LangContext.Provider value={{ lang, L, toggleLang }}>
      {children}
    </LangContext.Provider>
  )
}
