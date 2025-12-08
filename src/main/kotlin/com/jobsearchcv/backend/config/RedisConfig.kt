package com.jobsearchcv.backend.config

import io.lettuce.core.ClientOptions
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer
import org.springframework.data.redis.stream.StreamMessageListenerContainer
import java.time.Duration

@Configuration
@ConditionalOnProperty(
    name = ["redis.streams.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class RedisConfig {

    private val log: Logger = LoggerFactory.getLogger(RedisConfig::class.java)

    @Value("\${redis.host:localhost}")
    private lateinit var redisHost: String

    @Value("\${redis.port:6379}")
    private var redisPort: Int = 6379

    @Value("\${redis.password:}")
    private var redisPassword: String? = null

    @Value("\${redis.streams.consumer-group:backend-processors}")
    private lateinit var consumerGroup: String

    @Value("\${redis.streams.request-stream:job-search:requests}")
    private lateinit var requestStream: String

    @Value("\${redis.streams.result-stream:job-search:results}")
    private lateinit var resultStream: String

    @Value("\${redis.streams.dlq-stream:job-search:dlq}")
    private lateinit var dlqStream: String

    @Value("\${redis.connection.timeout-ms:5000}")
    private var connectionTimeoutMs: Long = 5000

    @Value("\${redis.connection.command-timeout-ms:10000}")
    private var commandTimeoutMs: Long = 10000

    @PostConstruct
    fun initialize() {
        log.info(
            "Redis Streams configuration initialized: " +
                    "host=$redisHost, port=$redisPort, " +
                    "consumer-group=$consumerGroup, " +
                    "request-stream=$requestStream, " +
                    "result-stream=$resultStream, " +
                    "dlq-stream=$dlqStream"
        )

        // Validate configuration
        require(redisHost.isNotBlank()) { "redis.host must not be blank" }
        require(redisPort in 1..65535) { "redis.port must be valid port number" }
        require(consumerGroup.isNotBlank()) { "redis.streams.consumer-group must not be blank" }
    }

    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory {
        val redisConfig = RedisStandaloneConfiguration(redisHost, redisPort)

        // Set password if provided
        if (!redisPassword.isNullOrBlank()) {
            redisConfig.setPassword(redisPassword)
        }

        // Configure Lettuce client with timeouts and reconnect
        val clientConfig = LettucePoolingClientConfiguration.builder()
            .commandTimeout(Duration.ofMillis(commandTimeoutMs))
            .clientOptions(
                ClientOptions.builder()
                    .autoReconnect(true)
                    .build()
            )
            .build()

        val factory = LettuceConnectionFactory(redisConfig, clientConfig)
        factory.afterPropertiesSet()
        log.info("RedisConnectionFactory created for $redisHost:$redisPort")
        return factory
    }

    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, String> {
        val template = RedisTemplate<String, String>()
        template.connectionFactory = connectionFactory

        // Use String serializers for keys and values
        val stringSerializer = StringRedisSerializer()
        template.keySerializer = stringSerializer
        template.valueSerializer = stringSerializer
        template.hashKeySerializer = stringSerializer
        template.hashValueSerializer = stringSerializer

        template.afterPropertiesSet()
        log.info("RedisTemplate configured with String serializers")
        return template
    }

    @Bean
    fun streamMessageListenerContainer(
        connectionFactory: RedisConnectionFactory
    ): StreamMessageListenerContainer<String, MapRecord<String, String, String>> {
        val container = StreamMessageListenerContainer.create(
            connectionFactory,
            StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                .pollTimeout(Duration.ofSeconds(2))
                .batchSize(10)
                .build()
        )
        container.start()
        log.info("StreamMessageListenerContainer started")
        return container
    }

    // Expose configuration values as beans for injection
    @Bean
    fun redisConsumerGroup(): String = consumerGroup

    @Bean
    fun redisRequestStream(): String = requestStream

    @Bean
    fun redisResultStream(): String = resultStream

    @Bean
    fun redisDlqStream(): String = dlqStream
}
