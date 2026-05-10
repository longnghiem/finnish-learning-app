package me.longng.finnish_learning_backend.config

import me.longng.finnish_learning_backend.event.QuizAnswerEvent
import me.longng.finnish_learning_backend.event.QuizEventProducer
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer
import org.springframework.kafka.support.serializer.JacksonJsonSerializer

/**
 * Kafka configuration for the Finnish Learning App.
 *
 * Spring Boot 4.x removed Kafka auto-configuration (including `KafkaProperties`,
 * auto-configured `KafkaTemplate`, `ConsumerFactory`, and `KafkaListenerContainerFactory`).
 * This class manually defines all required beans:
 *
 * - [KafkaAdmin] for topic management and auto-creation
 * - [ProducerFactory] + [KafkaTemplate] for publishing [QuizAnswerEvent]s
 * - [ConsumerFactory] + [ConcurrentKafkaListenerContainerFactory] for `@KafkaListener` consumers
 * - [NewTopic] for the `quiz-answers` topic declaration
 *
 * [@EnableKafka] activates Spring Kafka's `@KafkaListener` annotation processing.
 * Without it, `@KafkaListener` methods are silently ignored.
 */
@Configuration
@EnableKafka
class KafkaConfig(
    @Value("\${spring.kafka.bootstrap-servers}") private val bootstrapServers: String,
    @Value("\${spring.kafka.consumer.group-id}") private val consumerGroupId: String,
) {

    // ── Admin ────────────────────────────────────────────────────

    /**
     * [KafkaAdmin] manages Kafka topics. When [NewTopic] beans are present,
     * KafkaAdmin auto-creates them on startup if they don't already exist.
     */
    @Bean
    fun kafkaAdmin(): KafkaAdmin {
        val configs = mapOf<String, Any>(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
        )
        return KafkaAdmin(configs)
    }

    // ── Producer ─────────────────────────────────────────────────

    /**
     * Creates a [ProducerFactory] for [QuizAnswerEvent] messages.
     *
     * - [StringSerializer]: message key is "userId-cardId"
     * - [JacksonJsonSerializer]: value serialized as JSON via Jackson
     *
     * Note: [JacksonJsonSerializer] is the non-deprecated replacement for
     * `JsonSerializer` in spring-kafka 4.x (Jackson 3 support).
     */
    @Bean
    fun quizAnswerProducerFactory(): ProducerFactory<String, QuizAnswerEvent> {
        val props = mapOf<String, Any>(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JacksonJsonSerializer::class.java,
        )
        return DefaultKafkaProducerFactory(props)
    }

    /**
     * Typed [KafkaTemplate] for publishing [QuizAnswerEvent]s.
     *
     * Defined explicitly (rather than relying on auto-configuration) because:
     * - Spring Boot 4.x has no Kafka auto-configuration
     * - Provides compile-time type safety
     * - Eliminates IDE "Could not autowire" warnings
     */
    @Bean
    fun quizAnswerKafkaTemplate(
        quizAnswerProducerFactory: ProducerFactory<String, QuizAnswerEvent>,
    ): KafkaTemplate<String, QuizAnswerEvent> {
        return KafkaTemplate(quizAnswerProducerFactory)
    }

    // ── Consumer ─────────────────────────────────────────────────

    /**
     * Creates a [ConsumerFactory] for [QuizAnswerEvent] messages.
     *
     * - [StringDeserializer]: message keys are strings
     * - [JacksonJsonDeserializer]: values deserialized from JSON via Jackson
     * - `trusted.packages`: restricts deserialization to our event package
     *   (security measure — prevents arbitrary class instantiation)
     * - `auto.offset.reset=earliest`: new consumer groups start from the
     *   beginning of the topic (ensures no events are missed on first deploy)
     */
    @Bean
    fun quizAnswerConsumerFactory(): ConsumerFactory<String, QuizAnswerEvent> {
        val props = mapOf<String, Any>(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to consumerGroupId,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to JacksonJsonDeserializer::class.java,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            // Restrict which packages Jackson is allowed to deserialize into.
            // Without this, an attacker could craft a message that instantiates
            // arbitrary classes (deserialization attack).
            "spring.json.trusted.packages" to "me.longng.finnish_learning_backend.event",
        )
        return DefaultKafkaConsumerFactory(props)
    }

    /**
     * [ConcurrentKafkaListenerContainerFactory] is required for `@KafkaListener`
     * methods to work. It creates the listener containers that poll Kafka and
     * dispatch messages to annotated methods.
     *
     * Without this bean, Spring Kafka will throw:
     * "No qualifying bean of type 'KafkaListenerContainerFactory'" at startup.
     */
    @Bean
    fun kafkaListenerContainerFactory(
        quizAnswerConsumerFactory: ConsumerFactory<String, QuizAnswerEvent>,
    ): ConcurrentKafkaListenerContainerFactory<String, QuizAnswerEvent> {
        return ConcurrentKafkaListenerContainerFactory<String, QuizAnswerEvent>().apply {
            setConsumerFactory(quizAnswerConsumerFactory)
        }
    }

    // ── Topics ───────────────────────────────────────────────────

    /**
     * The `quiz-answers` topic for quiz answer events.
     *
     * - 3 partitions: allows consumer parallelism (multiple app instances
     *   can share the workload via the consumer group)
     * - 1 replica: single-node dev setup (matches docker-compose's single broker)
     */
    @Bean
    fun quizAnswersTopic(): NewTopic {
        return TopicBuilder
            .name(QuizEventProducer.TOPIC_NAME)
            .partitions(3)
            .replicas(1)
            .build()
    }
}
