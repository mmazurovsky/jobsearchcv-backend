package com.jobsearchcv.backend.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
class RootController {

    @GetMapping("/")
    fun root(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(
            mapOf(
                "service" to "ApplyFirst API",
                "status" to "running",
                "timestamp" to Instant.now().toString(),
            )
        )
    }
}
