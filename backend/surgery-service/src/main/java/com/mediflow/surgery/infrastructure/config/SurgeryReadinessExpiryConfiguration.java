package com.mediflow.surgery.infrastructure.config;

import com.mediflow.surgery.application.port.in.ExpireSurgeryReadinessUseCase;
import com.mediflow.surgery.application.port.in.QueryExpiredSurgeryReadinessUseCase;
import com.mediflow.surgery.application.port.in.RecordSurgeryReadinessExpiryRetryUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@Profile("!test")
@EnableScheduling
@ConditionalOnProperty(prefix = "mediflow", name = {
        "features.surgery.enabled", "surgery.readiness.expiry.enabled"}, havingValue = "true")
public class SurgeryReadinessExpiryConfiguration {
    @Bean
    SurgeryReadinessExpiryWorker surgeryReadinessExpiryWorker(QueryExpiredSurgeryReadinessUseCase query,
            ExpireSurgeryReadinessUseCase expire, RecordSurgeryReadinessExpiryRetryUseCase retry,
            @Value("${mediflow.surgery.readiness.expiry.batch-size:20}") int batchSize) {
        return new SurgeryReadinessExpiryWorker(query, expire, retry, batchSize);
    }
}
