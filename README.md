# Finnish Learning App

Monorepo for a full-stack Finnish vocabulary flashcard app. Two independent projects:

- [`backend/`](./backend/README.md) — Spring Boot REST API
- [`frontend/`](./frontend/README.md) — React SPA

See each subdirectory's README for full setup, env vars, and commands.

---

## Backend

Spring Boot 4 / Kotlin 2.2 / Java 21 API backed by PostgreSQL 16 (jOOQ + Flyway), Kafka (KRaft), and JWT auth via Spring Security. SpringDoc OpenAPI exposes Swagger UI at `/swagger-ui.html`.

Quiz answers are written to the DB (source of truth) and published to the `quiz-answers` Kafka topic; a consumer maintains the pre-aggregated `user_topic_stats` table that powers the progress dashboard.

Quick start (from `backend/`):
```
docker compose up -d        # PostgreSQL + Kafka
./gradlew bootRun           # app on :8080
```
Requires a `.env` with DB / JWT / Kafka settings — see [backend/README.md](./backend/README.md).

---

## Frontend

React 19 + TypeScript SPA built with Vite 8, Tailwind 4, React Router 7, TanStack Query 5, and Zod. Features topic-based flashcards, search, dark/light theme, EN/FI i18n, JWT auth with role-based access (ADMIN for card management), and a progress dashboard.

Quick start (from `frontend/`):
```
npm install
npm run dev                 # http://localhost:5173
```
Override the backend URL with `VITE_API_BASE_URL` in `frontend/.env.local`. Details in [frontend/README.md](./frontend/README.md).