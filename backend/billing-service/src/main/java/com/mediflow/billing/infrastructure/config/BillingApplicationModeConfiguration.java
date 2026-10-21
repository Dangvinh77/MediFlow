package com.mediflow.billing.infrastructure.config;

import com.mediflow.billing.application.port.out.OutpatientMvpMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Mirrors clinical/lab's own outpatient-mvp mode detection (profile OR explicit property). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({OutpatientPublicationProperties.class, OutpatientRefundPublicationProperties.class})
public class BillingApplicationModeConfiguration {

    @Bean
    OutpatientMvpMode outpatientMvpMode(Environment environment,
            @Value("${mediflow.features.outpatient-mvp.enabled:false}") boolean configured) {
        return new OutpatientMvpMode(configured || environment.acceptsProfiles(Profiles.of("outpatient-mvp")));
    }
}
