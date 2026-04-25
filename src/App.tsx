import "react-quizlet-flashcard/dist/index.css";
import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {BrowserRouter, Route, Routes} from "react-router-dom";
import {AuthProvider} from "./auth/AuthProvider.tsx";
import {Navbar} from "./components/Navbar.tsx";
import {LandingPage} from "./pages/LandingPage.tsx";
import {LoginPage} from "./pages/LoginPage.tsx";
import {TopicPage} from "./pages/TopicPage.tsx";

/**
 * TanStack Query client with sensible defaults.
 *
 * - `staleTime`: 5 minutes — avoids unnecessary refetches for data
 *   that doesn't change often (topics, cards).
 * - `retry`: 1 — retries once on failure before showing an error.
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000,
      retry: 1,
    },
  },
});

/**
 * Root application component.
 *
 * Route structure:
 * - `/` → LandingPage (topic grid)
 * - `/login` → LoginPage
 * - `/topics/:topicId` → TopicPage (flashcard viewer)
 */
export default function App() {
  return (
      <BrowserRouter>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <div className="min-h-screen bg-gray-50">
              <Navbar />
              <main>
                <Routes>
                  <Route path="/" element={<LandingPage />} />
                  <Route path="/login" element={<LoginPage />} />
                  <Route path="/topics/:topicId" element={<TopicPage />} />
                </Routes>
              </main>
            </div>
          </AuthProvider>
        </QueryClientProvider>
      </BrowserRouter>
  );
}