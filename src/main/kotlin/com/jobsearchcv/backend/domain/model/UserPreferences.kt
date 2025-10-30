package com.jobsearchcv.backend.domain.model

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import java.time.OffsetDateTime
import java.util.*

@Document(collection = "user_preferences")
@Schema(description = "User preferences and settings")
data class UserPreferences(
    @Id
    @Schema(description = "Unique identifier for the preferences", example = "pref-123e4567", required = true)
    val id: String = UUID.randomUUID().toString(),

    @Indexed(unique = true)
    @field:Field("user_id")
    @Schema(description = "ID of the user who owns these preferences", required = true)
    val userId: String,

    @field:Field("is_marketing_subscribed")
    @Schema(description = "Whether user is subscribed to marketing and product updates newsletter", example = "false", required = true)
    val isMarketingSubscribed: Boolean = false,

    @field:Field("created_at")
    @Schema(description = "Timestamp when the preferences were created", required = true)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @field:Field("updated_at")
    @Schema(description = "Timestamp when the preferences were last updated", required = true)
    val updatedAt: OffsetDateTime = OffsetDateTime.now()
) {
    companion object {
        fun createDefault(userId: String): UserPreferences {
            return UserPreferences(
                userId = userId,
                isMarketingSubscribed = false
            )
        }
    }
}

// Response DTOs
@Schema(description = "User preferences response")
data class UserPreferencesResponse(
    @Schema(description = "Unique identifier", example = "pref-123e4567")
    val id: String,
    @Schema(description = "User ID", example = "user-123e4567")
    val userId: String,
    @Schema(description = "Marketing newsletter subscription status", example = "false")
    val isMarketingSubscribed: Boolean,
    @Schema(description = "Created timestamp")
    val createdAt: OffsetDateTime,
    @Schema(description = "Updated timestamp")
    val updatedAt: OffsetDateTime
)

@Schema(description = "Request to update marketing subscription")
data class UpdateMarketingSubscriptionRequest(
    @Schema(description = "Marketing newsletter subscription status", example = "true", required = true)
    val isSubscribed: Boolean
)

@Schema(description = "Response for preference operations")
data class PreferenceOperationResponse(
    @Schema(description = "Operation success status", example = "true")
    val success: Boolean,
    @Schema(description = "Response message", example = "Preferences updated successfully")
    val message: String,
    @Schema(description = "Updated preferences object")
    val preferences: UserPreferences?
)
