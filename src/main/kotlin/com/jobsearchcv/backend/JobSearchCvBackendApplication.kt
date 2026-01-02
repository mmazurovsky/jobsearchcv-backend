package com.jobsearchcv.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(
    exclude = [RedisRepositoriesAutoConfiguration::class]  // We use Redis for Streams, not repositories
)
@EnableScheduling
@EnableAsync
@EnableMongoRepositories
class JobSearchCvBackendApplication

fun main(args: Array<String>) {
    val context = runApplication<JobSearchCvBackendApplication>(*args)
} 