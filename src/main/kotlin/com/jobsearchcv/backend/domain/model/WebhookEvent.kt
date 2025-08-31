package com.jobsearchcv.backend.domain.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import java.time.OffsetDateTime
import java.util.*

@Document(collection = "stripe_webhook_events")
@CompoundIndexes(
    CompoundIndex(name = "stripe_event_id_idx", def = "{'stripe_event_id': 1}", unique = true),
    CompoundIndex(name = "processed_at_idx", def = "{'processed_at': -1}")
)
data class StripeWebhookEvent(
    @Id
    val id: String = UUID.randomUUID().toString(),
    
    @field:Field("stripe_event_id")
    val stripeEventId: String,
    
    @field:Field("event_type")
    val eventType: String,
    
    @field:Field("processed_successfully")
    val processedSuccessfully: Boolean,
    
    @field:Field("error_message")
    val errorMessage: String? = null,
    
    @field:Field("retry_count")
    val retryCount: Int = 0,
    
    @field:Field("created_at")
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    
    @field:Field("processed_at")
    val processedAt: OffsetDateTime = OffsetDateTime.now()
)