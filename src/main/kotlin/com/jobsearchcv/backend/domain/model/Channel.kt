package com.jobsearchcv.backend.domain.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Notification channel type", enumAsRef = true)
enum class Channel(val value: String) {
    /**
     * Email channel - Email addresses are retrieved from Firebase, not stored in destinations collection.
     * This value is used internally but cannot be set via the destination API.
     */
    EMAIL("email"),
    TELEGRAM("telegram"),
    XCOM("xcom"),
    PAGE("page");

    companion object {
        fun fromString(value: String): Channel {
            return values().find { it.value == value.lowercase() }
                ?: throw IllegalArgumentException("Unknown channel: $value")
        }
    }
}