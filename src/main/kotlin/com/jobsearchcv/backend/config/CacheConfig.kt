package com.jobsearchcv.backend.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

/**
 * Cache configuration using Caffeine for in-memory caching.
 * Industry standard solution for high-performance local caching.
 */
@Configuration
@EnableCaching
class CacheConfig {

    companion object {
        const val PAGE_JOBS_CACHE = "pageJobs"
    }

    @Bean
    fun cacheManager(): CacheManager {
        val cacheManager = CaffeineCacheManager(PAGE_JOBS_CACHE)
        cacheManager.setCaffeine(caffeineCacheBuilder())
        return cacheManager
    }

    private fun caffeineCacheBuilder(): Caffeine<Any, Any> {
        return Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(1000) // Limit cache size to prevent memory issues
            .recordStats() // Enable statistics for monitoring
    }
}
