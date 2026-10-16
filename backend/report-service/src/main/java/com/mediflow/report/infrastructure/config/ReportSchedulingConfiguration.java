package com.mediflow.report.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** One bounded Report-owned scheduler, not a shared host worker pool. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ReportSchedulingConfiguration {
    @Bean
    ThreadPoolTaskScheduler reportTaskScheduler(@Value("${mediflow.report.scheduler.pool-size:1}") int size) {
        if (size < 1 || size > 2) throw new IllegalArgumentException("Report scheduler pool must be 1..2");
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(size);
        scheduler.setThreadNamePrefix("report-recovery-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setAwaitTerminationSeconds(5);
        return scheduler;
    }
}
