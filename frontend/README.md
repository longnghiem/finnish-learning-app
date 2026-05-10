# Finnish Learning Frontend

A React/TypeScript single-page application for learning Finnish vocabulary through interactive flashcards.

---

## Tech Stack

- **UI Framework**: [React](https://react.dev/) ^19
- **Language**: [TypeScript](https://www.typescriptlang.org/) ~6
- **Build Tool**: [Vite](https://vite.dev/) ^8
- **Routing**: [React Router DOM](https://reactrouter.com/) ^7
- **Server State**: [TanStack React Query](https://tanstack.com/query) ^5
- **Styling**: [Tailwind CSS](https://tailwindcss.com/) ^4
- **Schema Validation**: [Zod](https://zod.dev/) ^4
- **Linting**: ESLint + typescript-eslint ^10 / ^8
- **Formatting**: [Prettier](https://prettier.io/) ^3

The design was implemented by **Claude Design**

---

## Features

- **Topic-based flashcards** – Browse vocabulary grouped by topic; flip cards to reveal English translations and example sentences.
- **Search** – Filter cards by Finnish word or example sentence within a topic.
- **Dark / Light theme** – Toggle persisted in `localStorage`.
- **i18n** – UI available in English and Finnish; preference persisted in `localStorage`.
- **Authentication** – Register + login against backend, token persisted in localStorage, attached as Authorization: Bearer <token> to mutating requests.
  Role-based access: ADMIN role required for card management; regular users see Dashboard only.
- **Admin panel** – Create, edit, and delete flashcards (authenticated users only).

---

## Pages

- `/` — Landing page (topic selection grid)
- `/topics/:topicId` — Flashcard viewer with search and pagination
- `/register` — Registration page (creates account + auto-login)
- `/login` — Login page
- `/admin` — Card management page (admin only)
- `/dashboard` — Progress dashboard (authenticated users only): per-topic stats, streaks, accuracy, due cards

---

## Getting Started

### Install & Run

```bash
npm install
npm run dev
```

The app will be available at **http://localhost:5173**.

### Other Scripts

```bash
npm run build     # Type-check and build for production
npm run preview   # Preview the production build locally
npm run lint      # Run ESLint
```

---

## Environment Variables

Create a `.env.local` file in this directory to override defaults:

- `VITE_API_BASE_URL` — Base URL of the backend API. Default: `http://localhost:8080`

---

## Project Structure

```
src/
├── api/          # API client (auth, cards, topics, config)
├── assets/       # Static assets (topic images)
├── auth/         # Auth context, provider, and hook
├── components/   # Reusable UI components (Navbar, Flashcard, modals…)
├── hooks/        # Custom React hooks (useCards, useTopics)
├── lang/         # i18n strings and LangProvider
├── pages/        # Route-level page components
├── schemas/      # Zod validation schemas
├── theme/        # Theme context, provider, and hook
└── types/        # Shared TypeScript types
└── styles.ts     # Shared Tailwind class strings 
```

---

## To Do

1. Fix card management page – Create card modal is missing topic selection
2. Token expiry handling