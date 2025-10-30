package com.jobsearchcv.backend.domain.model

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import java.time.OffsetDateTime
import java.util.*

@Document(collection = "destinations")
@Schema(description = "User notification destination")
data class Destination(
    @Id
    @Schema(description = "Unique identifier for the destination", example = "dest-123e4567-e89b-12d3-a456-426614174000", required = true)
    val id: String,
    @Indexed(unique = true) @field:Field("user_id")
    @Schema(description = "ID of the user who owns this destination", required = true)
    val userId: String,
    @field:Field("channel") 
    @Schema(description = "Channel type (email, telegram)", example = "email", required = true)
    val channel: String, // Stored as string in DB but used as enum in code
    @field:Field("channel_value") 
    @Schema(description = "Channel value (email address, telegram chat ID)", example = "user@example.com", required = true)
    val channelValue: String,
    @field:Field("created_at")
    @Schema(description = "Timestamp when the destination was created", required = true)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
) {
    // Helper property to work with enum in code
    val channelEnum: Channel
        get() = Channel.fromString(channel)
    
    companion object {
        fun createNew(userId: String, channel: Channel, channelValue: String): Destination {
            return Destination(
                id = UUID.randomUUID().toString(),
                userId = userId,
                channel = channel.value,
                channelValue = channelValue,
                createdAt = OffsetDateTime.now()
            )
        }
    }
}