package com.mediflow.clinical.infrastructure.config;

import com.mediflow.clinical.application.port.out.OutpatientMvpMode;
import com.mediflow.clinical.application.port.out.RecordAccessPolicyMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration(proxyBeanMethods = false)
public class ClinicalApplicationModeConfiguration {

    @Bean
    OutpatientMvpMode outpatientMvpMode(
            Environment environment,
            @Value("${mediflow.features.outpatient-mvp.enabled:false}") boolean configured) {
        return new OutpatientMvpMode(configured || environment.acceptsProfiles(Profiles.of("outpatient-mvp")));
    }

    @Bean
    RecordAccessPolicyMode recordAccessPolicyMode(
            @Value("${mediflow.v2.record-access-policy:false}") boolean enabled) {
        return new RecordAccessPolicyMode(enabled);
    }
}
