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
        name = "user_id_created_at_idx",
        def = "{'user_id': 1, 'created_at': -1}"
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
    
    @field:Field("s3_bucket")
    val s3Bucket: String,
    
    @field:Field("s3_key")
    val s3Key: String,
    
    @field:Field("created_at")
    val createdAt: OffsetDateTime = OffsetDateTime.now()
) {
    companion object {
        fun create(
            userId: String,
            linkToCv: String,
            fileSize: Long,
            contentType: String,
            s3Bucket: String,
            s3Key: String
        ): SimpleUserCV {
            return SimpleUserCV(
                userId = userId,
                linkToCv = linkToCv,
                fileSize = fileSize,
                contentType = contentType,
                s3Bucket = s3Bucket,
                s3Key = s3Key
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
    val s3Bucket: String,
    val s3Key: String
) {
    fun toSimpleUserCV(): SimpleUserCV {
        return SimpleUserCV.create(
            userId = userId,
            linkToCv = linkToCv,
            fileSize = fileSize,
            contentType = contentType,
            s3Bucket = s3Bucket,
            s3Key = s3Key
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