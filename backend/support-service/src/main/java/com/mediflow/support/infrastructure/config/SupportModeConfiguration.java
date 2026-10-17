package com.mediflow.support.infrastructure.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class SupportModeConfiguration {
    @Bean
    static BeanFactoryPostProcessor supportModuleGuard(Environment environment) {
        return beans -> {
            if (!environment.matchesProfiles("report-module") || !environment.matchesProfiles("notification-module")
                    || !environment.matchesProfiles("outpatient-mvp", "mvp-outpatient")) {
                throw new IllegalStateException("Support requires Report/Notification module and outpatient profiles");
            }
            for (String feature : new String[]{"report.financial-read", "report.financial-archive",
                    "report.financial-publication", "report.operational-consumer", "report.operational-publication",
                    "report.settlement-consumer", "report.final-recognition-consumer", "notification.surgery-consumer",
                    "notification.surgery-payment-request-consumer"}) {
                if (environment.getProperty("mediflow." + feature + ".enabled", Boolean.class, false)) {
                    throw new IllegalStateException("Deferred feature cannot run in Support: " + feature);
                }
            }
        };
    }
}
