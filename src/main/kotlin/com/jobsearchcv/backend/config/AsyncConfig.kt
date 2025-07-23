package com.jobsearchcv.backend.config

import org.slf4j.LoggerFactory
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

@Configuration
@EnableAsync
class AsyncConfig : AsyncConfigurer {

    private val logger = LoggerFactory.getLogger(AsyncConfig::class.java)

    @Bean(name = ["jobProcessingExecutor"])
    override fun getAsyncExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 5
        executor.maxPoolSize = 10
        executor.queueCapacity = 100
        executor.setThreadNamePrefix("JobProcessing-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(60)
        executor.initialize()
        return executor
    }

    override fun getAsyncUncaughtExceptionHandler(): AsyncUncaughtExceptionHandler {
        return AsyncUncaughtExceptionHandler { exception, method, params ->
            logger.error(
                "Async method execution failed: ${method.name} with params: ${params.contentToString()}",
                exception
            )
        }
    }
}