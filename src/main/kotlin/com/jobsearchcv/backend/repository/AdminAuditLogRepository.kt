package com.jobsearchcv.backend.repository

import com.jobsearchcv.backend.domain.model.AdminAuditLog
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface AdminAuditLogRepository : MongoRepository<AdminAuditLog, String> {
    fun findByTargetUserIdOrderByTimestampDesc(targetUserId: String): List<AdminAuditLog>
    fun findByActionAndTimestampAfter(action: String, timestamp: OffsetDateTime): List<AdminAuditLog>
}