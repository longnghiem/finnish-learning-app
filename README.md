# Finnish Learning App

Monorepo for a full-stack Finnish vocabulary flashcard app. Two independent projects:

- [`backend/`](./backend/README.md) — Spring Boot REST API
- [`frontend/`](./frontend/README.md) — React SPA
- [`deploy/`](./deploy/README.md) — AWS EC2 deployment (scripts, systemd units, nginx, runbook)

See each subdirectory's README for full setup, env vars, and commands.

---

## Try the live demo

The app is deployed on AWS EC2 (Amazon Linux 2023, t3.micro, Free Tier) and is reachable at:

**http://ec2-51-20-56-106.eu-north-1.compute.amazonaws.com/**

Demo credentials:

| Field    | Value    |
|----------|----------|
| Username | `demo`   |
| Password | `123456` |

Suggested walkthrough:

1. Open the URL above and log in with the credentials.
2. From the topic list, open **"Vapaa-aika ja harrastukset"** (Free time and hobbies) to browse the flashcards.
3. Practice a card by **flipping it** and writing a sample sentence using the word — the **AI sentence evaluation** is only available once the card is flipped. Submit the sentence to get AI feedback on grammar and usage.
4. When ready, start the **quiz** from that topic.
5. Open the **Dashboard** to see your per-topic stats — answer counts, accuracy, and recent activity.
6. To see the **SM-2 spaced repetition** scheduler in action: register a fresh account, run a quiz so each card gets an initial review, then come back on subsequent days. Cards you answered correctly resurface later (intervals grow: 1 day → ~6 days → longer), while cards you got wrong reappear the next day. The per-card next-review date is computed server-side in [`SpacedRepetition.kt`](./backend/src/main/kotlin/me/longng/finnish_learning_backend/domain/SpacedRepetition.kt).

> Note: plain HTTP, single EC2 instance, no TLS. 

---

## Architecture deployed on AWS

```
┌────────────────────────────────────────────────────────────────────────┐
│  EC2  t3.micro  (Amazon Linux 2023, 1 GB RAM + 4 GB swap, 30 GB disk)  │
│                                                                         │
│  nginx :80   ←  the only public entry point                             │
│    ├─ /              →  /var/www/finnish/dist/   (static SPA)           │
│    └─ /api/*         →  127.0.0.1:8080           (Spring Boot)          │
│                                                                         │
│  Spring Boot jar      :8080   (systemd, -Xmx384m, Amazon Corretto 21)   │
│  Kafka KRaft broker   :9092   (systemd, -Xmx256m, loopback only)        │
│  PostgreSQL 15        :5432                      (loopback only)        │
└────────────────────────────────────────────────────────────────────────┘
```

Only port 80 is open in the EC2 Security Group. Postgres, Kafka, and the JVM bind to `127.0.0.1` and are unreachable from the internet. nginx terminates the public connection and reverse-proxies the API to the backend.

Full step-by-step runbook (AWS Console setup, provisioning, deployment) lives in [`deploy/README.md`](./deploy/README.md).

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