package com.mediflow.report.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class CareFinanceFeaturePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FeaturePropertiesConfiguration.class);

    @Test
    void careFinanceV2_defaultsToDisabled() {
        contextRunner.run(context -> assertThat(context.getBean(CareFinanceFeatureProperties.class)
                .careFinanceV2()).isFalse());
    }

    @Test
    void careFinanceV2_canBeExplicitlyEnabledForFutureRollout() {
        contextRunner.withPropertyValues("mediflow.features.care-finance-v2=true")
                .run(context -> assertThat(context.getBean(CareFinanceFeatureProperties.class)
                        .careFinanceV2()).isTrue());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CareFinanceFeatureProperties.class)
    static class FeaturePropertiesConfiguration {
    }
}
