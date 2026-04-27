import { createContext } from 'react'

export interface ThemeContextType {
  dark: boolean
  toggleDark: () => void
}

export const ThemeContext = createContext<ThemeContextType>({ dark: false, toggleDark: () => {} })

