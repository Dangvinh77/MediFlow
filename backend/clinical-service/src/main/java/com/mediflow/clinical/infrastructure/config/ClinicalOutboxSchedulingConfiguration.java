package com.mediflow.clinical.infrastructure.config;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Owns the single scheduled-task registrar for both care-finance and durable-event modes. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Conditional(ClinicalOutboxActivationCondition.class)
public class ClinicalOutboxSchedulingConfiguration {
}
