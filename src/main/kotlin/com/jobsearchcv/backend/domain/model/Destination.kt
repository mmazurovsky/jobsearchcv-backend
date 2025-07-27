package com.jobsearchcv.backend.domain.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import java.util.*

@Document(collection = "destinations")
data class Destination(
    @Id val id: String = UUID.randomUUID().toString(),
    @Indexed(unique = false) @field:Field("user_id") val userId: String,
    @field:Field("channel") val channel: String, // Stored as string in DB but used as enum in code
    @field:Field("channel_value") val channelValue: String
) {
    // Helper property to work with enum in code
    val channelEnum: Channel
        get() = Channel.fromString(channel)
    
    companion object {
        fun create(userId: String, channel: Channel, channelValue: String): Destination {
            return Destination(
                userId = userId,
                channel = channel.value,
                channelValue = channelValue
            )
        }
    }
}