package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.domain.model.AdminAuditLog
import com.jobsearchcv.backend.repository.AdminAuditLogRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AdminAuditService(
    private val adminAuditLogRepository: AdminAuditLogRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    fun logAdminAction(
        action: String,
        targetUserId: String,
        details: String? = null,
        success: Boolean = true,
        errorMessage: String? = null
    ) {
        try {
            val auditLog = AdminAuditLog(
                action = action,
                targetUserId = targetUserId,
                details = details,
                success = success,
                errorMessage = errorMessage
            )
            
            adminAuditLogRepository.save(auditLog)
            logger.debug("Logged admin action: {} for user: {} (success: {})", action, targetUserId, success)
            
        } catch (e: Exception) {
            logger.error("Failed to log admin action: {} for user: {}", action, targetUserId, e)
            // Don't throw exception to avoid breaking the main operation
        }
    }
    
    fun getAuditLogsForUser(userId: String): List<AdminAuditLog> {
        return try {
            adminAuditLogRepository.findByTargetUserIdOrderByTimestampDesc(userId)
        } catch (e: Exception) {
            logger.error("Failed to retrieve audit logs for user: {}", userId, e)
            emptyList()
        }
    }
}