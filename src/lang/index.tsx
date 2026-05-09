import { createContext, useContext, useState, type ReactNode } from 'react'

export const I18N = {
  en: {
    appName: 'Finnish Learning App',
    chooseATopic: 'Choose a topic',
    login: 'Log in', logout: 'Logout',
    register: 'Register',
    confirmPassword: 'Confirm password',
    passwordsMustMatch: 'Passwords must match.',
    passwordTooShort: 'Password must be at least 6 characters.',
    alreadyHaveAccount: 'Already have an account?',
    noAccount: "Don't have an account?",
    dashboard: 'Dashboard',
    allTopics: '← All topics',
    word: 'Word', sentence: 'Sentence',
    searchByWord: 'Search by word…', searchBySentence: 'Search by sentence…',
    noCardsFound: 'No cards found.', createCard: 'Create card',
    edit: 'Edit', delete: 'Delete', cancel: 'Cancel', save: 'Save', create: 'Create',
    tapToFlip: 'tap to flip', admin: 'Manage cards',
    deleteConfirmMsg: 'Are you sure you want to delete this card? This action cannot be undone.',
    prev: '← Prev', next: 'Next →',
    finnishWord: 'Finnish word', exSentence: 'Example sentence', engTranslation: 'English translation',
    username: 'Username', password: 'Password',
    fillAllFields: 'Please fill in all fields.',
    enterCreds: 'Please enter both username and password.',
    manageCards: 'Card management', noCardsYet: 'No cards in this topic yet.',
    allTopicsFilter: 'All topics', backToTopics: '← Back to topics',
    cardOf: (a: number, b: number) => `${a} / ${b}`,
    // — Quiz —
    startQuiz: 'Start Quiz',
    quizTitle: 'Quiz',
    whatsTheFinnishWord: "What's the Finnish word?",
    again: 'Again',
    hard: 'Hard',
    good: 'Good',
    easy: 'Easy',
    noCardsDue: 'No cards due for review! 🎉',
    quizComplete: 'Quiz Complete!',
    cardsReviewed: 'Cards reviewed',
    accuracy: 'Accuracy',
    quizAgain: 'Quiz again',
    backToTopic: 'Back to topic',
    newCard: 'New',
    // — Dashboard / progress —
    progressTitle: 'Your Progress',
    totalReviews: 'Total Reviews',
    overallAccuracy: 'Overall Accuracy',
    currentStreak: 'Current Streak',
    bestStreak: 'Best Streak',
    cardsDueToday: 'Cards Due Today',
    learned: 'Learned',
    due: 'due',
    noProgressYet: 'No progress yet! Start a quiz to begin tracking your learning.',
  },
  fi: {
    appName: 'Suomen Oppiminen',
    chooseATopic: 'Valitse aihe',
    login: 'Kirjaudu', logout: 'Kirjaudu ulos',
    register: 'Rekisteröidy',
    confirmPassword: 'Vahvista salasana',
    passwordsMustMatch: 'Salasanojen on täsmättävä.',
    passwordTooShort: 'Salasanan on oltava vähintään 6 merkkiä.',
    alreadyHaveAccount: 'Onko sinulla jo tili?',
    noAccount: 'Eikö sinulla ole tiliä?',
    dashboard: 'Hallintapaneeli',
    allTopics: '← Kaikki aiheet',
    word: 'Sana', sentence: 'Lause',
    searchByWord: 'Hae sanalla…', searchBySentence: 'Hae lauseella…',
    noCardsFound: 'Kortteja ei löydy.', createCard: 'Luo kortti',
    edit: 'Muokkaa', delete: 'Poista', cancel: 'Peruuta', save: 'Tallenna', create: 'Luo',
    tapToFlip: 'napauta kääntääksesi', admin: 'Hallitse kortteja',
    deleteConfirmMsg: 'Haluatko varmasti poistaa tämän kortin? Toimintoa ei voi peruuttaa.',
    prev: '← Edellinen', next: 'Seuraava →',
    finnishWord: 'Suomenkielinen sana', exSentence: 'Esimerkkilause', engTranslation: 'Englanninkielinen käännös',
    username: 'Käyttäjänimi', password: 'Salasana',
    fillAllFields: 'Täytä kaikki kentät.',
    enterCreds: 'Syötä käyttäjänimi ja salasana.',
    manageCards: 'Korttien hallinta', noCardsYet: 'Tässä aiheessa ei ole vielä kortteja.',
    allTopicsFilter: 'Kaikki aiheet', backToTopics: '← Takaisin aiheisiin',
    cardOf: (a: number, b: number) => `${a} / ${b}`,
    // — Quiz —
    startQuiz: 'Aloita tietovisa',
    quizTitle: 'Tietovisa',
    whatsTheFinnishWord: 'Mikä on suomenkielinen sana?',
    again: 'Uudelleen',
    hard: 'Vaikea',
    good: 'Hyvä',
    easy: 'Helppo',
    noCardsDue: 'Ei tarkistettavia kortteja! 🎉',
    quizComplete: 'Tietovisa valmis!',
    cardsReviewed: 'Tarkistetut kortit',
    accuracy: 'Tarkkuus',
    quizAgain: 'Uusi tietovisa',
    backToTopic: 'Takaisin aiheeseen',
    newCard: 'Uusi',
    // — Dashboard / progress —
    progressTitle: 'Edistymisesi',
    totalReviews: 'Arvostelut yhteensä',
    overallAccuracy: 'Kokonaistarkkuus',
    currentStreak: 'Nykyinen putki',
    bestStreak: 'Paras putki',
    cardsDueToday: 'Tänään tarkistettavat kortit',
    learned: 'Opittu',
    due: 'tarkistettavaa',
    noProgressYet: 'Ei vielä edistystä! Aloita tietovisa seurataksesi oppimistasi.',
  },
}

export type Lang = 'en' | 'fi'
export type I18nStrings = typeof I18N.en

interface LangContextType {
  lang: Lang
  L: I18nStrings
  toggleLang: () => void
}

const LangContext = createContext<LangContextType>({ lang: 'en', L: I18N.en, toggleLang: () => {} })

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

export const useLang = () => useContext(LangContext)