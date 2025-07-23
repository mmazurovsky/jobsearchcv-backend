package com.jobsearchcv.backend.config

import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component

@Component("mongodb")
class MongoHealthIndicator(
    private val mongoTemplate: MongoTemplate
) : HealthIndicator {
    
    override fun health(): Health {
        return try {
            mongoTemplate.db.runCommand(org.bson.Document("ping", 1))
            Health.up()
                .withDetail("database", mongoTemplate.db.name)
                .build()
        } catch (e: Exception) {
            Health.down()
                .withException(e)
                .build()
        }
    }
}
