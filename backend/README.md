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
Quiz answer → DB (source of truth) → QuizEventProducer → quiz-answers topic → QuizStatsConsumer → user_topic_stats
```

## AI Sentence Evaluation

Authenticated users can submit a Finnish sentence (with the target word + meaning) via `POST /api/evaluate-sentence`.
The backend forwards it to the [Groq](https://groq.com/) chat-completions API (default model: `llama-3.3-70b-versatile`)
and returns structured feedback: CEFR level, grammar mistake flag, typo flag, whether the target word was used correctly,
an optional correction, and free-form feedback.

A per-user `DailyQuotaTracker` enforces `GROQ_DAILY_QUOTA` requests per day **before** the upstream call to avoid burning 
credits on rate-limited users. Excess requests get `429`. If `GROQ_API_KEY` is blank, evaluation is disabled.

- System prompt: `src/main/resources/prompts/sentence-evaluation-system-prompt.txt`

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
GROQ_MODEL=llama-3.3-70b-versatile
GROQ_DAILY_QUOTA=50             # per-user requests per day
```