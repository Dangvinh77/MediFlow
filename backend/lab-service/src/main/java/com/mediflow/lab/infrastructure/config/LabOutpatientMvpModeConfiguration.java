package com.mediflow.lab.infrastructure.config;

import com.mediflow.lab.application.port.out.LabOutpatientMvpMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration(proxyBeanMethods = false)
public class LabOutpatientMvpModeConfiguration {

    @Bean
    LabOutpatientMvpMode labOutpatientMvpMode(
            Environment environment,
            @Value("${mediflow.features.outpatient-mvp.enabled:false}") boolean configured) {
        return new LabOutpatientMvpMode(configured || environment.acceptsProfiles(Profiles.of("outpatient-mvp")));
    }
}
