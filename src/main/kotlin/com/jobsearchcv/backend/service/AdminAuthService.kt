package com.jobsearchcv.backend.service

import com.jobsearchcv.backend.config.AdminConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AdminAuthService(
    private val adminConfig: AdminConfig
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    fun validateAdminSecret(providedSecret: String?): Boolean {
        if (providedSecret.isNullOrBlank()) {
            logger.warn("Admin operation attempted with empty secret")
            return false
        }
        
        val isValid = providedSecret == adminConfig.secret
        if (!isValid) {
            logger.warn("Admin operation attempted with invalid secret")
        } else {
            logger.info("Valid admin secret provided for operation")
        }
        
        return isValid
    }
    
    fun requireAdminSecret(providedSecret: String?) {
        if (!validateAdminSecret(providedSecret)) {
            throw IllegalArgumentException("Invalid admin secret")
        }
    }
}