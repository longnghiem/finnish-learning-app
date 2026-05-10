package me.longng.finnish_learning_backend

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Testcontainers configuration shared by all integration tests.
 *
 * Provides:
 * - A PostgreSQL container (auto-configures `spring.datasource.*` via @ServiceConnection)
 * - A Kafka container with manual property override for `spring.kafka.bootstrap-servers`
 *   (Spring Boot 4.x removed @ServiceConnection support for Kafka)
 *
 * Both containers use the same images as `docker-compose.yml` for consistency.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer {
        return PostgreSQLContainer(DockerImageName.parse("postgres:16"))
    }

    @Bean
    fun kafkaContainer(): KafkaContainer {
        return KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1"))
    }

    /**
     * Overrides `spring.kafka.bootstrap-servers` with the Testcontainers Kafka
     * broker's dynamically assigned address.
     *
     * [DynamicPropertyRegistrar] is the Spring Boot 3.2+ / 4.x way to register
     * dynamic properties from @TestConfiguration classes (where @DynamicPropertySource
     * static methods are not available).
     */
    @Bean
    fun kafkaProperties(kafkaContainer: KafkaContainer): DynamicPropertyRegistrar {
        return DynamicPropertyRegistrar { registry ->
            registry.add("spring.kafka.bootstrap-servers") { kafkaContainer.bootstrapServers }
        }
    }
}
