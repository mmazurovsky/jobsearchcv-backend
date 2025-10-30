package com.jobsearchcv.backend.repository

import com.jobsearchcv.backend.domain.model.UserSubscription
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface SubscriptionRepository : MongoRepository<UserSubscription, String> {
    fun findByUserId(userId: String): UserSubscription?
    fun findByStripeCustomerId(customerId: String): UserSubscription?
    fun findByStripeSubscriptionId(subscriptionId: String): UserSubscription?
}