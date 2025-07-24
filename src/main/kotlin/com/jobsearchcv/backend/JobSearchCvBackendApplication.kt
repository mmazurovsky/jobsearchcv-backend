package com.jobsearchcv.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableAsync
class CoreServiceApplication

fun main(args: Array<String>) {
    val context = runApplication<CoreServiceApplication>(*args)
} 