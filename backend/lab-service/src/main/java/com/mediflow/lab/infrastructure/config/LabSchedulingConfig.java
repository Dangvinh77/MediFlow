package com.mediflow.lab.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the transactional outbox relay. */
@Configuration
@EnableScheduling
public class LabSchedulingConfig {
}
