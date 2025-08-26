package com.jobsearchcv.backend.domain.model

data class EmailContent(
    val recipient: String,
    val subject: String,
    val htmlBody: String,
    val textBody: String
)