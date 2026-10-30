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
- `/essay/:topicId` — Short-essay writing page (authenticated users only): prompt dropdown, 300–2,500 character editor with live counter, and the AI evaluation result — CEFR level, on-topic flag, and per-issue fixes
- `/ask` — "Ask Himmel", the recruiter chatbot (public, no login): Cloudflare Turnstile check -> chat about Long's professional background with live progress steps. `?tab=fit` opens the "Check my fit" tab

---

## Getting Started

### Prerequisites

- **Node.js** `^20.19.0 || >=22.12.0` (required by Vite 8). Tested on Node 20.20.
- **npm** `>=10` (ships with the Node versions above).

Check your versions:

```bash
node -v
npm -v
```

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
npm run format    # Format all files with Prettier (.prettierrc)
```

---

## Environment Variables

The frontend reads two build-time variables, baked into the JS bundle by `vite build`.
Vite picks env files by mode automatically.

`VITE_API_BASE_URL` is the backend's address:

| File              | Loaded by    | Value                   | Purpose                                          |
| ----------------- | ------------ | ----------------------- | ------------------------------------------------ |
| `.env`            | `vite dev`   | `http://localhost:8080` | Local development against a backend on :8080.    |
| `.env.production` | `vite build` | _(empty)_               | Same-origin requests behind nginx reverse proxy. |
| `.env.local`      | all modes    | _(your override)_       | Per-developer overrides; not committed.          |

Resolution rules (see `src/api/config.ts`):

- `undefined` → dev fallback `http://localhost:8080`.
- `""` → same-origin (production behind reverse proxy).
- absolute URL → cross-origin to that backend.

`VITE_TURNSTILE_SITE_KEY` is the public site key of the Cloudflare Turnstile widget on `/ask`.
`.env` holds Cloudflare's test key `1x00000000000000000000AA` (always passes; pairs with the
backend's test secret `1x0000000000000000000000000000000AA`). For production, create the widget
in the Cloudflare dashboard (Turnstile → Add widget, mode Managed) and put its site key in
`.env.production`; confirm the price and any DNS needs there. Without a key, `/ask` says the
check isn't set up.

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
├── types/        # Shared TypeScript types
├── contact.ts    # Long's email and LinkedIn (portfolio page, chatbot notices)
└── styles.ts     # Shared Tailwind class strings
```
