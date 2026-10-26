package com.mediflow.clinical.infrastructure.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "mediflow.clinical.scheduling", name = "enabled", havingValue = "true")
public class ClinicalSchedulingFeatureConfiguration {
    @Bean(name = "clinicalSchedulingClock")
    public Clock clinicalSchedulingClock() {
        return Clock.systemUTC();
    }
}
