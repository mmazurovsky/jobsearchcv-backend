package com.jobsearchcv.backend.controller

import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/test")
class TestController {
    
    private val log = LoggerFactory.getLogger(TestController::class.java)
    
    @GetMapping("/ping")
    fun ping(): Map<String, String> {
        log.info("Received ping request")
        return mapOf(
            "status" to "ok",
            "message" to "pong",
            "timestamp" to System.currentTimeMillis().toString()
        )
    }
    
    @PostMapping("/echo")
    fun echo(@RequestBody body: Map<String, Any>): Map<String, Any> {
        log.info("Received echo request with body: $body")
        return mapOf(
            "echo" to body,
            "timestamp" to System.currentTimeMillis()
        )
    }
}