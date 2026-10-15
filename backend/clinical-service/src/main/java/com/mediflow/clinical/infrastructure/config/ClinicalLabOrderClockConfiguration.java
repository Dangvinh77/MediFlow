package com.mediflow.clinical.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides an independently qualified clock for immutable Lab source-order facts. */
@Configuration(proxyBeanMethods = false)
public class ClinicalLabOrderClockConfiguration {

    @Bean
    Clock clinicalLabOrderClock() {
        return Clock.systemUTC();
    }
}
