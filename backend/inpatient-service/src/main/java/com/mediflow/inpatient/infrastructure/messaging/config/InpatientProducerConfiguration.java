package com.mediflow.inpatient.infrastructure.messaging.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "mediflow.inpatient.messaging.producer.enabled", havingValue = "true")
public class InpatientProducerConfiguration {
}
