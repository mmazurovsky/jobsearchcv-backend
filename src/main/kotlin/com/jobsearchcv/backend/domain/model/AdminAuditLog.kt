package com.jobsearchcv.backend.domain.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import java.time.OffsetDateTime
import java.util.*

@Document(collection = "admin_audit_logs")
data class AdminAuditLog(
    @Id
    val id: String = UUID.randomUUID().toString(),
    
    @field:Field("action")
    val action: String, // "ACTIVATE_PREMIUM", "REVOKE_PREMIUM", "CHECK_STATUS"
    
    @field:Field("target_user_id")
    val targetUserId: String,
    
    @field:Field("details")
    val details: String?, // Additional details about the operation
    
    @field:Field("success")
    val success: Boolean,
    
    @field:Field("error_message")
    val errorMessage: String? = null,
    
    @field:Field("timestamp")
    val timestamp: OffsetDateTime = OffsetDateTime.now()
)