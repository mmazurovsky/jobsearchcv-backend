package com.jobsearchcv.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableMongoRepositories
class JobSearchCvBackendApplication

fun main(args: Array<String>) {
    val context = runApplication<JobSearchCvBackendApplication>(*args)
} 