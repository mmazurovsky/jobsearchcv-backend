package com.jobsearchcv.backend.repository

import com.jobsearchcv.backend.domain.model.StripeWebhookEvent
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface StripeWebhookEventRepository : MongoRepository<StripeWebhookEvent, String> {
    fun findByStripeEventId(stripeEventId: String): StripeWebhookEvent?
    fun existsByStripeEventId(stripeEventId: String): Boolean
}