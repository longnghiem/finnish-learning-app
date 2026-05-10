import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthProvider.tsx'
import { ThemeProvider } from './theme'
import { LangProvider } from './lang'
import { Navbar } from './components/Navbar.tsx'
import { LandingPage } from './pages/LandingPage.tsx'
import { LoginPage } from './pages/LoginPage.tsx'
import { TopicPage } from './pages/TopicPage.tsx'
import { AdminPage } from './pages/AdminPage.tsx'
import {RegisterPage} from "./pages/RegisterPage.tsx";
import {QuizPage} from "./pages/QuizPage.tsx";
import {DashboardPage} from "./pages/DashboardPage.tsx";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 5 * 60 * 1000, retry: 1 },
  },
})

function AppInner() {
  return (
    <div className="min-h-screen flex flex-col bg-bg text-text-primary transition-[background,color] duration-300 ease-in-out">
      <Navbar />
      <main className="flex-1">
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/topics/:topicId" element={<TopicPage />} />
          <Route path="/quiz/:topicId" element={<QuizPage />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/admin" element={<AdminPage />} />
        </Routes>
      </main>
    </div>
  )
}

export default function App() {
  return (
    <ThemeProvider>
      <LangProvider>
        <BrowserRouter>
          <QueryClientProvider client={queryClient}>
            <AuthProvider>
              <AppInner />
            </AuthProvider>
          </QueryClientProvider>
        </BrowserRouter>
      </LangProvider>
    </ThemeProvider>
  )
}
