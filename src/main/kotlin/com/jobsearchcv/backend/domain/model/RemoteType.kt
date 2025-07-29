package com.jobsearchcv.backend.domain.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Remote work preference", enumAsRef = true)
enum class RemoteType {
    `On-site`,
    Remote,
    Hybrid;

    val label: String
        get() = name

    companion object {
        fun fromLabel(label: String): RemoteType? {
            return values().find { it.name.equals(label, ignoreCase = true) }
        }

        fun getDefault(): RemoteType = Remote

        fun getAllLabels(): List<String> = values().map { it.name }
    }
} 