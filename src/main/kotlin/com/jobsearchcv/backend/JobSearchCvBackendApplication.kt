package com.jobsearchcv.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(
    exclude = [
        RedisRepositoriesAutoConfiguration::class,  // We use Redis for Streams, not repositories
        org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration::class  // Prevent auto-connection to localhost:6379 when Redis is disabled
    ]
)
@EnableScheduling
@EnableAsync
@EnableMongoRepositories
class JobSearchCvBackendApplication

fun main(args: Array<String>) {
    val context = runApplication<JobSearchCvBackendApplication>(*args)
} 