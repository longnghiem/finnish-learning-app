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
- **Testing**: JUnit 5, Testcontainers (PostgreSQL), Mockito

## Run commands

- To run the application
```
./gradlew bootRun 
```

- To generate jOOQ classes under `build/generated-sources/jooq`
```
./gradlew generateJooq
```

## Swagger UI

Once the application is running, you can access the API documentation and test the endpoints using Swagger UI at:
http://localhost:8080/swagger-ui.html

Raw OpenAPI JSON spec: http://localhost:8080/api-docs

Raw OpenAPI YAML spec: http://localhost:8080/api-docs.yaml

## .evn file

Create at the project root. 

```
# Database connection 
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/finnish_learning_app
SPRING_DATASOURCE_USERNAME=changeme
SPRING_DATASOURCE_PASSWORD=changeme

# Local directory for storing uploaded card images
IMAGE_STORAGE_LOCATION=./uploads
```