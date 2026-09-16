package com.mediflow.clinical.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.cloud.openfeign.FeignClientProperties;
import org.springframework.context.annotation.Configuration;

class OpenFeignConfigurationTest {

    @Test
    void applicationConfiguration_bindsOpenFeignTimeoutsAndCircuitBreaker() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(FeignPropertiesConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    FeignClientProperties properties = context.getBean(FeignClientProperties.class);
                    FeignClientProperties.FeignClientConfiguration defaults =
                            properties.getConfig().get("default");

                    assertThat(defaults).isNotNull();
                    assertThat(defaults.getConnectTimeout()).isEqualTo(2000);
                    assertThat(defaults.getReadTimeout()).isEqualTo(3000);
                    assertThat(context.getEnvironment()
                            .getProperty("spring.cloud.openfeign.circuitbreaker.enabled", Boolean.class))
                            .isTrue();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FeignClientProperties.class)
    static class FeignPropertiesConfiguration {
    }
}
