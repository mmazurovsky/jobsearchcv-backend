package com.jobsearchcv.backend.repository

import com.jobsearchcv.backend.domain.model.Destination
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface DestinationRepository : MongoRepository<Destination, String> {
    fun findByUserIdAndChannelAndChannelValue(userId: String, channel: String, channelValue: String): Destination?
    fun findByUserId(userId: String): List<Destination>
}