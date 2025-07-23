package com.jobsearchcv.backend.config

import org.quartz.Scheduler
import org.quartz.SchedulerException
import org.quartz.SchedulerFactory
import org.quartz.impl.StdSchedulerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*

@Configuration
class QuartzConfig {
    
    @Bean
    fun schedulerFactory(): SchedulerFactory {
        val properties = Properties().apply {
            setProperty("org.quartz.scheduler.instanceName", "JobSearchScheduler")
            setProperty("org.quartz.scheduler.instanceId", "AUTO")
            setProperty("org.quartz.threadPool.threadCount", "10")
            setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore")
        }
        
        val factory = StdSchedulerFactory()
        factory.initialize(properties)
        return factory
    }
    
    @Bean
    @Throws(SchedulerException::class)
    fun scheduler(schedulerFactory: SchedulerFactory): Scheduler {
        val scheduler = schedulerFactory.scheduler
        scheduler.start()
        return scheduler
    }
} 