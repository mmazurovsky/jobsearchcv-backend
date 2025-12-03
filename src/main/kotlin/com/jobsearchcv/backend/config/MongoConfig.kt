package com.jobsearchcv.backend.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.jobsearchcv.backend.domain.model.JobSearchOut
import com.jobsearchcv.backend.domain.model.JobSearchPrompt
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*

@Configuration
@EnableMongoRepositories(basePackages = ["com.jobsalerts.core.repository"])
class MongoConfig {

    companion object {
        private val logger = LoggerFactory.getLogger(MongoConfig::class.java)
    }

    @Bean
    fun mappingMongoConverter(
        databaseFactory: MongoDatabaseFactory,
        customConversions: MongoCustomConversions,
        mappingContext: MongoMappingContext
    ): MappingMongoConverter {
        val converter = MappingMongoConverter(databaseFactory, mappingContext)
        converter.setCustomConversions(customConversions)
        // Remove the _class field from documents
        converter.setTypeMapper(DefaultMongoTypeMapper(null))
        return converter
    }

    @Bean
    fun mongoCustomConversions(): MongoCustomConversions {
        return MongoCustomConversions(
            listOf(
                OffsetDateTimeWriteConverter(),
                OffsetDateTimeReadConverter(),
                OffsetDateTimeStringReadConverter(),
                StringOffsetDateTimeWriteConverter(),
            )
        )
    }

    /**
     * Ensures MongoDB indexes are created on application startup.
     * This is idempotent - safe to run multiple times.
     */
    @Bean
    fun initializeMongoIndexes(mongoTemplate: MongoTemplate) = ApplicationRunner {
        try {
            logger.info("Initializing MongoDB indexes...")

            // Ensure indexes for JobSearchPrompt collection
            val jobSearchPromptIndexOps = mongoTemplate.indexOps(JobSearchPrompt::class.java)
            jobSearchPromptIndexOps.ensureIndex(
                org.springframework.data.mongodb.core.index.Index()
                    .on("user_id", org.springframework.data.domain.Sort.Direction.ASC)
                    .named("user_id_idx")
            )
            jobSearchPromptIndexOps.ensureIndex(
                org.springframework.data.mongodb.core.index.Index()
                    .on("created_at", org.springframework.data.domain.Sort.Direction.DESC)
                    .named("created_at_idx")
            )
            // Compound index is defined via @CompoundIndex annotation and will be created automatically
            logger.info("✓ JobSearchPrompt indexes created/verified")

            // Ensure indexes for JobSearchOut collection (including promptId)
            val jobSearchOutIndexOps = mongoTemplate.indexOps(JobSearchOut::class.java)
            jobSearchOutIndexOps.ensureIndex(
                org.springframework.data.mongodb.core.index.Index()
                    .on("prompt_id", org.springframework.data.domain.Sort.Direction.ASC)
                    .named("prompt_id_idx")
            )
            logger.info("✓ JobSearchOut indexes created/verified (including prompt_id)")

            logger.info("MongoDB indexes initialization completed successfully")

        } catch (e: Exception) {
            logger.error("Failed to initialize MongoDB indexes: ${e.message}", e)
            // Don't throw - let the application start even if index creation fails
            // Indexes can be created manually or will be created on first use
        }
    }
}

class OffsetDateTimeReadConverter : Converter<Date?, OffsetDateTime?> {
    override fun convert(date: Date): OffsetDateTime {
        return date.toInstant().atOffset(ZoneOffset.UTC).withNano(0)
    }
}

class OffsetDateTimeWriteConverter : Converter<OffsetDateTime?, Date?> {
    override fun convert(offsetDateTime: OffsetDateTime): Date {
        return Date.from(offsetDateTime.withNano(0).toInstant())
    }
}

class OffsetDateTimeStringReadConverter : Converter<String?, OffsetDateTime?> {
    override fun convert(source: String): OffsetDateTime? {
        return try {
            // Try ISO_OFFSET_DATE_TIME format first
            OffsetDateTime.parse(source, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        } catch (e: DateTimeParseException) {
            try {
                // Fallback to ISO_LOCAL_DATE_TIME and add UTC offset
                val localDateTime = java.time.LocalDateTime.parse(source, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                localDateTime.atOffset(ZoneOffset.UTC)
            } catch (e2: DateTimeParseException) {
                try {
                    // Try custom format
                    OffsetDateTime.parse(source, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS"))
                        .withOffsetSameInstant(ZoneOffset.UTC)
                } catch (e3: DateTimeParseException) {
                    null
                }
            }
        }
    }
}

class StringOffsetDateTimeWriteConverter : Converter<OffsetDateTime?, String?> {
    override fun convert(source: OffsetDateTime): String {
        return source.withOffsetSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
}
