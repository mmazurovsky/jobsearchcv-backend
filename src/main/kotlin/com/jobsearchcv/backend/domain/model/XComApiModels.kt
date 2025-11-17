package com.jobsearchcv.backend.domain.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Request model for posting a tweet to X.com
 * Maps to the POST /tweet endpoint
 */
data class TweetRequest(
    @JsonProperty("username")
    val username: String,

    @JsonProperty("text")
    val text: String,

    @JsonProperty("media_ids")
    val mediaIds: List<String>? = null
)

/**
 * Response model from X.com tweet posting
 */
data class TweetResponse(
    @JsonProperty("success")
    val success: Boolean,

    @JsonProperty("message")
    val message: String,

    @JsonProperty("tweet_id")
    val tweetId: String?,

    @JsonProperty("username")
    val username: String
)

/**
 * Error response from X.com API
 */
data class TweetErrorResponse(
    @JsonProperty("detail")
    val detail: String? = null,

    @JsonProperty("error")
    val error: String? = null
)
