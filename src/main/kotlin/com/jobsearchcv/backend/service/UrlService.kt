package com.jobsearchcv.backend.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class UrlService(
    @param:Value("\${app.website.url}") private val websiteUrl: String,
    @param:Value("\${app.support.email}") private val supportEmail: String
) {
    
    fun getWebsiteUrl(): String = websiteUrl
    
    fun getSupportEmail(): String = supportEmail
    
    fun getEditJobSearchUrl(alertId: String): String = "$websiteUrl/#/editJobSearch/$alertId"
    
    fun getUnsubscribeUrl(): String = "$websiteUrl/#/changeEmailSubscriptions"
}