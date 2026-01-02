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
    @field:Field("page_path")
    @Schema(description = "Page path for PAGE channel destinations", example = "us-tech-jobs", required = false)
    val pagePath: String? = null,
    @field:Field("social_media_tags")
    @Schema(description = "Hashtags to include in social media posts", example = "[\"#techjobs\", \"#remotework\", \"#hiring\"]", required = false)
    val socialMediaTags: List<String>? = null,
    @field:Field("post_on_x")
    @Schema(description = "Whether to post page overviews on X (Twitter). Only applicable for PAGE channel destinations.", example = "true", required = false)
    val postOnX: Boolean? = null,
    @field:Field("created_at")
    @Schema(description = "Timestamp when the destination was created", required = true)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
) {
    // Helper property to work with enum in code
    val channelEnum: Channel
        get() = Channel.fromString(channel)
    
    companion object {
        fun createNew(
            userId: String,
            channel: Channel,
            channelValue: String,
            pagePath: String? = null,
            socialMediaTags: List<String>? = null,
            postOnX: Boolean? = null
        ): Destination {
            return Destination(
                id = UUID.randomUUID().toString(),
                userId = userId,
                channel = channel.value,
                channelValue = channelValue,
                pagePath = pagePath,
                socialMediaTags = socialMediaTags,
                postOnX = postOnX,
                createdAt = OffsetDateTime.now()
            )
        }
    }
}