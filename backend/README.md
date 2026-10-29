# Finnish Learning Backend

A Spring Boot backend for managing Finnish vocabulary flashcards.

## Tech Stack

- **Language**: Kotlin 2.2.21
- **Framework**: Spring Boot 4.0.5
- **Runtime**: Java 21
- **Database**: PostgreSQL 16 (JDBC driver 42.7.2)
- **ORM/Query Builder**: jOOQ
- **Database Migrations**: Flyway
- **Security**: Spring Security (currently configured to permit all)
- **API Documentation**: SpringDoc OpenAPI / Swagger
- **Build Tool**: Gradle 8.x
- **Messaging**: Apache Kafka 7.9.0 (KRaft mode, no ZooKeeper)
- **Authentication**: JWT (JJWT 0.12.x)
- **Testing**: JUnit 5, Testcontainers (PostgreSQL, Kafka), Mockito

## Run commands

- To generate jOOQ classes under `build/generated-sources/jooq`
```
./gradlew generateJooq
```

- To run the application
```
./gradlew bootRun 
```

## Swagger UI

Once the application is running, you can access the API documentation and test the endpoints using Swagger UI at:
http://localhost:8080/swagger-ui.html

Raw OpenAPI JSON spec: http://localhost:8080/api-docs

Raw OpenAPI YAML spec: http://localhost:8080/api-docs.yaml

## Kafka

### Architecture

When a user submits a quiz answer, a `QuizAnswerEvent` is published to the `quiz-answers` Kafka topic by `QuizEventProducer`. 
`QuizStatsConsumer` reads from this topic and updates the pre-aggregated `user_topic_stats` table (used by progress/dashboard endpoints).

```
  Quiz answer
    → QuizService.submitAnswer  [@Transactional]
         → DB: review_schedule (source of truth)
         → publishEvent(QuizAnswerEvent)            (in-process, held until commit)
    → COMMIT
    → QuizAnswerEventListener  [@TransactionalEventListener(AFTER_COMMIT)]
         → QuizEventProducer → quiz-answers topic
    → QuizStatsConsumer → user_topic_stats

```

## AI Sentence Evaluation

Authenticated users can submit a Finnish sentence (with the target word + meaning) via `POST /api/evaluate-sentence`.
The backend forwards it to the [Groq](https://groq.com/) chat-completions API (default model: `openai/gpt-oss-120b`)
and returns structured feedback: CEFR level, grammar mistake flag, typo flag, whether the target word was used correctly,
an optional correction, and free-form feedback.

A per-user `DailyQuotaTracker` enforces `GROQ_DAILY_QUOTA` requests per day **before** the upstream call to avoid burning 
credits on rate-limited users. Excess requests get `429`. If `GROQ_API_KEY` is blank, evaluation is disabled.

- System prompt: `src/main/resources/prompts/sentence-evaluation-system-prompt.md`

## AI Short Essay Evaluation

Authenticated users pick a prompt for a topic and submit a 300–2,500 character Finnish essay.
The backend grades it with **Claude Haiku 4.5 on AWS Bedrock** and returns a CEFR sub-level, an on-topic flag, 
a list of concrete grammar/typo issues with suggested fixes, and optional feedback.

### How Bedrock is used

- **Converse API**, AWS SDK for Java v2 (`software.amazon.awssdk:bedrockruntime`). 
- **Structured output**, not prompt-described JSON. The response contract is sent as
  `outputConfig.textFormat` with a JSON Schema (`ESSAY_RESPONSE_SCHEMA` in
  `BedrockEssayClient`), so the model cannot return a shape Jackson fails to bind.
- **EU inference profile** `eu.anthropic.claude-haiku-4-5-20251001-v1:0`, invoked from
  `eu-north-1`. 
- **Cost control before the call.** `EssayQuotaTracker` caps each user at
  `BEDROCK_DAILY_QUOTA` (20) evaluations per Helsinki day and is checked *before* the
  billable request, so a rate-limited user never triggers one.
- System prompt: `src/main/resources/prompts/essay-evaluation-system-prompt.md`

## Recruiter Chatbot ("Ask Himmel")

An AI agent that answers recruiters' questions about Long's professional background, anonymously,
on `/ask`. It runs on **Claude Haiku 4.5 on AWS Bedrock** and answers only from a curated profile,
which it reads through **MCP tools**. The design is in
[ADR 0002](../docs/adr/0002-recruiter-chatbot-mcp-architecture.md).

| Method | Path | What it does |
|---|---|---|
| POST | `/api/ask/session` | Checks the Cloudflare Turnstile token and returns a session id |
| POST | `/api/ask/chat` | Answers one message as a Server-Sent Events stream: progress steps, then the answer |
| GET | `/api/ask/stats` | Public totals: answers, blocked attacks, share of this month's budget used |
| POST | `/api/ask/fit-check` | Checks a pasted job description against the profile, as a Server-Sent Events stream: progress steps, then a verified report |

### How it uses AI

- **A hand-written agent loop with tool use** (`ChatService`, Bedrock Converse API). The model sees
  an index of the profile and two tools, `list_entries` and `get_entry`. It either answers or asks
  to read entries; the backend runs the reads and calls the model again. At most 3 rounds of up to
  4 reads, then one last call that must answer. No agent framework: every model call is budgeted
  before it runs.
- **MCP (Model Context Protocol) across two languages.** The tools are served by a separate,
  read-only TypeScript MCP server on **AWS Lambda** (`../mcp-server`). The backend talks to it with
  the official Java MCP SDK and signs every request with **AWS SigV4** using the EC2 instance's
  role, so there is no API key to leak. The function URL refuses unsigned requests.
- **Bedrock Guardrails before the model.** Each message is screened for prompt attacks and harmful
  content (`InvokeGuardrailChecks`), in Finnish and English. A blocked message never reaches the
  model.
- **Prompt caching.** One cache point moves to the end of the latest message, so a long
  conversation re-reads its earlier part from Bedrock's cache at a tenth of the input price.
- **Grounded answers.** The system prompt allows only facts from the profile and asks the model to
  say when something isn't in it. The conversation history is rebuilt from the database, never
  taken from the browser.
- **"Check my fit": structured output and hallucination checks.** A pasted job description goes
  through a two-phase agent: research calls that read entries over MCP, then one call with
  **forced, strict tool use**, so Bedrock guarantees a JSON-Schema-valid report.`QuoteVerifier` then 
  checks every quote against the profile and removes any false claims the model invented. 
- System prompts: `src/main/resources/prompts/recruiter-chat-system-prompt.md`,
  `src/main/resources/prompts/recruiter-fit-check-system-prompt.md`

### Cost and abuse control

- **A hard spending cap:** $0.25 a day, $5 a month (`app.ask.budget.*`). Before the first
  billable call, each request reserves its worst-case cost under a database lock; afterwards it
  settles the real cost, priced from Bedrock's token counts including cache reads and writes
  (`CostCalculator`). Parallel requests can't break the cap.
- **Anonymous, but limited:** a Cloudflare Turnstile bot check per conversation, per-IP limits
  on new conversations and messages, and at most 10 messages per conversation, because every
  message resends the whole conversation. IP addresses are stored only as a keyed hash (HMAC).
- **Streaming progress (SSE):** `/chat` returns an `SseEmitter` at once, and the work runs on the
  small `askExecutor` thread pool. The visitor sees "checking", "reading entries", "writing", then
  the whole answer. A visitor who leaves stops the turn, so no more model calls are paid for.
  Errors before the stream are normal HTTP errors with a `code`; later ones are `error` events
  (contract in `controller/dto/ask/AskSseEvents.kt`).

## Docker

Kafka runs in **KRaft mode** (`KAFKA_PROCESS_ROLES: broker,controller`), so no separate Zookeeper container is needed.

Start PostgreSQL and Kafka locally with Docker Compose:

```
docker compose up -d
```

Stop without removing data:
```
docker compose down
```

Stop and wipe all volumes (fresh slate):
```
docker compose down -v
```

### Inspect Kafka messages

To verify that `QuizAnswerEvent`s are being published to the `quiz-answers` topic (useful for debugging the producer → consumer pipeline):
```
docker exec finnish_learning_kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic quiz-answers --from-beginning
```
Prints all previously published events as JSON and keeps listening for new ones. Press `Ctrl+C` to stop.

## .env file

Create at the project root.

```
# Database connection
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5532/finnish_learning_app
SPRING_DATASOURCE_USERNAME=changeme
SPRING_DATASOURCE_PASSWORD=changeme

# Local directory for storing uploaded card images
IMAGE_STORAGE_LOCATION=./uploads

# JWT — generate a secret with: openssl rand -base64 32
JWT_SECRET=changeme
JWT_EXPIRATION_MS=86400000

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Groq sentence evaluator
GROQ_API_KEY=                  # Groq API key — leave blank to disable evaluation
GROQ_BASE_URL=https://api.groq.com/openai/v1
GROQ_MODEL=openai/gpt-oss-120b
GROQ_DAILY_QUOTA=50             # per-user requests per day

# Bedrock essay evaluator — LOCAL DEVELOPMENT ONLY.
AWS_BEARER_TOKEN_BEDROCK=       # Bedrock long-term API key (starts ABSK...) — set an expiry
AWS_REGION=eu-north-1

# Recruiter chatbot: key for hashing visitor IP addresses. REQUIRED, the app won't start without it.
ASK_IP_HASH_KEY=                # generate with: openssl rand -hex 32

# Recruiter chatbot: Cloudflare Turnstile secret. Blank = the chatbot can't create sessions.
# For local development use Cloudflare's test secret that always passes, from
# https://developers.cloudflare.com/turnstile/troubleshooting/testing/
TURNSTILE_SECRET_KEY=

```