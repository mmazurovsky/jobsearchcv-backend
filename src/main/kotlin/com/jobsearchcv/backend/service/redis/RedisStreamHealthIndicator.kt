package com.jobsearchcv.backend.service.redis

import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

@Component("redisStreams")
@ConditionalOnBean(RedisTemplate::class)
class RedisStreamHealthIndicator(
    private val redisTemplate: RedisTemplate<String, String>
) : HealthIndicator {

    override fun health(): Health {
        return try {
            val pong = redisTemplate.connectionFactory?.connection?.ping()
            if (pong != null) {
                Health.up()
                    .withDetail("status", "Connected")
                    .withDetail("ping", "PONG")
                    .build()
            } else {
                Health.down()
                    .withDetail("status", "No response")
                    .build()
            }
        } catch (e: Exception) {
            Health.down()
                .withDetail("status", "Connection failed")
                .withDetail("error", e.message)
                .build()
        }
    }
}
