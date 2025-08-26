package com.jobsearchcv.backend.domain.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import java.time.OffsetDateTime
import java.util.*

@Document(collection = "user_cvs")
@CompoundIndexes(
    CompoundIndex(
        name = "user_id_uploaded_at_idx",
        def = "{'user_id': 1, 'uploaded_at': -1}"
    )
)
data class SimpleUserCV(
    @Id
    val id: String = UUID.randomUUID().toString(),
    
    @field:Field("user_id")
    val userId: String,
    
    @field:Field("link_to_cv")
    val linkToCv: String,
    
    @field:Field("file_size")
    val fileSize: Long,
    
    @field:Field("content_type")
    val contentType: String,
    
    @field:Field("original_filename")
    val originalFilename: String? = null,
    
    @field:Field("storage_bucket")
    val storageBucket: String? = null,
    
    @field:Field("storage_path")
    val storagePath: String? = null,
    
    @field:Field("uploaded_at")
    val uploadedAt: OffsetDateTime = OffsetDateTime.now(),
    
    @field:Field("created_at")
    val createdAt: OffsetDateTime = OffsetDateTime.now()
) {
    companion object {
        fun create(
            userId: String,
            linkToCv: String,
            fileSize: Long,
            contentType: String,
            originalFilename: String? = null,
            storageBucket: String?,
            storagePath: String?,
            uploadedAt: OffsetDateTime = OffsetDateTime.now()
        ): SimpleUserCV {
            return SimpleUserCV(
                userId = userId,
                linkToCv = linkToCv,
                fileSize = fileSize,
                contentType = contentType,
                originalFilename = originalFilename,
                storageBucket = storageBucket,
                storagePath = storagePath,
                uploadedAt = uploadedAt
            )
        }
    }
}

// Input model for creating CV records
data class SimpleCVInput(
    val userId: String,
    val linkToCv: String,
    val fileSize: Long,
    val contentType: String,
    val originalFilename: String? = null,
    val storageBucket: String?,
    val storagePath: String?,
    val uploadedAt: OffsetDateTime = OffsetDateTime.now()
) {
    fun toSimpleUserCV(): SimpleUserCV {
        return SimpleUserCV.create(
            userId = userId,
            linkToCv = linkToCv,
            fileSize = fileSize,
            contentType = contentType,
            originalFilename = originalFilename,
            storageBucket = storageBucket,
            storagePath = storagePath,
            uploadedAt = uploadedAt
        )
    }
}

data class CVExtractedData(
    @field:Field("desired_job")
    val desiredJob: String? = null,

    @field:Field("location")
    val location: String? = null,

    @field:Field("job_types")
    val jobTypes: List<String> = emptyList(),

    @field:Field("remote_types")
    val remoteTypes: List<String> = emptyList(),

    @field:Field("exclusions")
    val exclusions: String? = null,

    @field:Field("recent_positions")
    val recentPositions: List<String> = emptyList(),

//    @field:Field("skills")
//    val skills: List<CVSkill> = emptyList(),

    @field:Field("technologies")
    val technologies: List<String> = emptyList()
)