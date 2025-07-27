package com.jobsearchcv.backend.domain.model

enum class Channel(val value: String) {
    EMAIL("email"),
    TELEGRAM("telegram");
    
    companion object {
        fun fromString(value: String): Channel {
            return values().find { it.value == value.lowercase() }
                ?: throw IllegalArgumentException("Unknown channel: $value")
        }
    }
}