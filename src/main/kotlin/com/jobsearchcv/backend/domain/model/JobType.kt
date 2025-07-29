package com.jobsearchcv.backend.domain.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Type of job position", enumAsRef = true)
enum class JobType {
    `Full-time`,
    `Part-time`,
    Contract,
    Temporary,
    Internship;

    val label: String
        get() = name

    companion object {
        fun fromLabel(label: String): JobType? {
            return values().find { it.name.equals(label, ignoreCase = true) }
        }

        fun getDefault(): JobType = `Full-time`

        fun getAllLabels(): List<String> = values().map { it.name }
    }
} 