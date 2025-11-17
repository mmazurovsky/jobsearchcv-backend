package com.jobsearchcv.backend.domain.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Notification channel type", enumAsRef = true)
enum class Channel(val value: String) {
    EMAIL("email"),
    TELEGRAM("telegram"),
    XCOM("xcom");
    
    companion object {
        fun fromString(value: String): Channel {
            return values().find { it.value == value.lowercase() }
                ?: throw IllegalArgumentException("Unknown channel: $value")
        }
    }
}