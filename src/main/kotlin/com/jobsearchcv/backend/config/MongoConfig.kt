package com.jobsearchcv.backend.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.jobsearchcv.backend.domain.model.*
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

            // Ensure indexes for JobSearchOut collection
            val jobSearchOutIndexOps = mongoTemplate.indexOps(JobSearchOut::class.java)

            // Index on prompt_id
            jobSearchOutIndexOps.ensureIndex(
                org.springframework.data.mongodb.core.index.Index()
                    .on("prompt_id", org.springframework.data.domain.Sort.Direction.ASC)
                    .named("prompt_id_idx")
            )

            // Compound index for user + approved status
            jobSearchOutIndexOps.ensureIndex(
                org.springframework.data.mongodb.core.index.Index()
                    .on("user_id", org.springframework.data.domain.Sort.Direction.ASC)
                    .on("is_approved", org.springframework.data.domain.Sort.Direction.ASC)
                    .named("user_approved_idx")
            )

            // Compound index for user + subscribed status
            jobSearchOutIndexOps.ensureIndex(
                org.springframework.data.mongodb.core.index.Index()
                    .on("user_id", org.springframework.data.domain.Sort.Direction.ASC)
                    .on("is_subscribed", org.springframework.data.domain.Sort.Direction.ASC)
                    .named("user_subscribed_idx")
            )

            // Compound index for user + approved + subscribed (for email job sending)
            jobSearchOutIndexOps.ensureIndex(
                org.springframework.data.mongodb.core.index.Index()
                    .on("user_id", org.springframework.data.domain.Sort.Direction.ASC)
                    .on("is_approved", org.springframework.data.domain.Sort.Direction.ASC)
                    .on("is_subscribed", org.springframework.data.domain.Sort.Direction.ASC)
                    .named("user_approved_subscribed_idx")
            )

            // Index on created_at for sorting by date
            jobSearchOutIndexOps.ensureIndex(
                org.springframework.data.mongodb.core.index.Index()
                    .on("created_at", org.springframework.data.domain.Sort.Direction.DESC)
                    .named("created_at_idx")
            )

            logger.info("✓ JobSearchOut indexes created/verified (prompt_id, user_id compounds, created_at)")

            // Ensure indexes for ProcessedJobData collection
            val processedJobIndexOps = mongoTemplate.indexOps(ProcessedJobData::class.java)

            // Index on internal_id for fast lookups by internal ID (used in public job endpoint)
            processedJobIndexOps.ensureIndex(
                org.springframework.data.mongodb.core.index.Index()
                    .on("internal_id", org.springframework.data.domain.Sort.Direction.ASC)
                    .unique()
                    .named("internal_id_unique_idx")
            )

            // Index on link for duplicate checking and link-based queries
            processedJobIndexOps.ensureIndex(
                org.springframework.data.mongodb.core.index.Index()
                    .on("link", org.springframework.data.domain.Sort.Direction.ASC)
                    .named("link_idx")
            )

            // Compound index for seniority filtering queries (internal_id + tags)
            processedJobIndexOps.ensureIndex(
                org.springframework.data.mongodb.core.index.Index()
                    .on("internal_id", org.springframework.data.domain.Sort.Direction.ASC)
                    .on("tags", org.springframework.data.domain.Sort.Direction.ASC)
                    .named("internal_id_tags_idx")
            )

            logger.info("✓ ProcessedJobData indexes created/verified (internal_id, link, compound)")

            // Ensure indexes for SentJobOut collection
            val sentJobIndexOps = mongoTemplate.indexOps(SentJobOut::class.java)

            // Compound index for user + job URL (duplicate detection per user)
            sentJobIndexOps.ensureIndex(
                org.springframework.data.mongodb.core.index.Index()
                    .on("user_id", org.springframework.data.domain.Sort.Direction.ASC)
                    .on("job_url", org.springframework.data.domain.Sort.Direction.ASC)
                    .named("user_job_idx")
            )

            // Compound index for user + sent_at (recent jobs per user)
            sentJobIndexOps.ensureIndex(
                org.springframework.data.mongodb.core.index.Index()
                    .on("user_id", org.springframework.data.domain.Sort.Direction.ASC)
                    .on("sent_at", org.springframework.data.domain.Sort.Direction.DESC)
                    .named("user_sent_at_idx")
            )

            // Index on destination for filtering by destination
            sentJobIndexOps.ensureIndex(
                org.springframework.data.mongodb.core.index.Index()
                    .on("destination", org.springframework.data.domain.Sort.Direction.ASC)
                    .named("destination_idx")
            )

            // Index on internal_id for lookups
            sentJobIndexOps.ensureIndex(
                org.springframework.data.mongodb.core.index.Index()
                    .on("internal_id", org.springframework.data.domain.Sort.Direction.ASC)
                    .named("internal_id_idx")
            )

            logger.info("✓ SentJobOut indexes created/verified (user_id compounds, destination, internal_id)")

            // Ensure indexes for UserPreferences collection
            val userPreferencesIndexOps = mongoTemplate.indexOps(UserPreferences::class.java)

            // Unique index on user_id (one preferences document per user)
            userPreferencesIndexOps.ensureIndex(
                org.springframework.data.mongodb.core.index.Index()
                    .on("user_id", org.springframework.data.domain.Sort.Direction.ASC)
                    .unique()
                    .named("user_id_unique_idx")
            )

            logger.info("✓ UserPreferences indexes created/verified (user_id unique)")

            // Ensure indexes for UserSubscription collection
            val userSubscriptionIndexOps = mongoTemplate.indexOps(UserSubscription::class.java)

            // Unique index on user_id (one subscription record per user)
            userSubscriptionIndexOps.ensureIndex(
                org.springframework.data.mongodb.core.index.Index()
                    .on("user_id", org.springframework.data.domain.Sort.Direction.ASC)
                    .unique()
                    .named("user_id_unique_idx")
            )

            // Index on stripe_customer_id for webhook lookups
            userSubscriptionIndexOps.ensureIndex(
                org.springframework.data.mongodb.core.index.Index()
                    .on("stripe_customer_id", org.springframework.data.domain.Sort.Direction.ASC)
                    .named("stripe_customer_id_idx")
            )

            logger.info("✓ UserSubscription indexes created/verified (user_id unique, stripe_customer_id)")

            // Ensure indexes for XComQueueJob collection
            val xcomQueueJobIndexOps = mongoTemplate.indexOps(XComQueueJob::class.java)

            // Compound index for queue worker (status + scheduled_at)
            xcomQueueJobIndexOps.ensureIndex(
                org.springframework.data.mongodb.core.index.Index()
                    .on("status", org.springframework.data.domain.Sort.Direction.ASC)
                    .on("scheduled_at", org.springframework.data.domain.Sort.Direction.ASC)
                    .named("status_scheduled_idx")
            )

            // Index on user_id for user-specific queries
            xcomQueueJobIndexOps.ensureIndex(
                org.springframework.data.mongodb.core.index.Index()
                    .on("user_id", org.springframework.data.domain.Sort.Direction.ASC)
                    .named("user_id_idx")
            )

            logger.info("✓ XComQueueJob indexes created/verified (status+scheduled_at compound, user_id)")

            // Ensure indexes for Destination collection
            val destinationIndexOps = mongoTemplate.indexOps(Destination::class.java)

            // Unique index on user_id (one destination per user for now)
            destinationIndexOps.ensureIndex(
                org.springframework.data.mongodb.core.index.Index()
                    .on("user_id", org.springframework.data.domain.Sort.Direction.ASC)
                    .unique()
                    .named("user_id_unique_idx")
            )

            logger.info("✓ Destination indexes created/verified (user_id unique)")

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
