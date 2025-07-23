package com.jobsearchcv.backend.config

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer
import com.fasterxml.jackson.datatype.jsr310.ser.OffsetDateTimeSerializer
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*


@Configuration
open class ObjectMapperConfiguration {

    @PostConstruct
    fun init() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        println("Default timezone set to UTC: " + Date())
    }

    @Bean
    @Primary
    open fun objectMapper(): ObjectMapper {
        val timeModule = JavaTimeModule().apply {
            addDeserializer(LocalDateTime::class.java, CustomLocalDateTimeDeserializer())
            addSerializer(LocalDateTime::class.java, CustomLocalDateTimeSerializer())

            // OffsetDateTime deserialization from strings like 2025-06-02T13:45:20.513+00:00
            // Uses built-in support, just ensure timestamps are disabled
        }

        return ObjectMapper()
            .registerModule(kotlinModule())
            .registerModule(timeModule)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .setVisibility(
                ObjectMapper().serializationConfig.defaultVisibilityChecker
                    .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
            )
    }
}

class CustomLocalDateTimeSerializer : StdSerializer<LocalDateTime>(LocalDateTime::class.java) {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")

    override fun serialize(value: LocalDateTime, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeString(value.format(formatter))
    }
}

class CustomLocalDateTimeDeserializer : StdDeserializer<LocalDateTime>(LocalDateTime::class.java) {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSSSSS]")

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): LocalDateTime {
        val text = p.text.trim()
        return LocalDateTime.parse(text, formatter)
    }
}
