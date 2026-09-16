package com.mediflow.lab.infrastructure.config;

import com.mediflow.lab.infrastructure.security.JwtAuthFilter;
import com.mediflow.lab.infrastructure.security.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSecretConfigurationTest {

    private static final String VALID_SECRET = "test-secret-must-have-at-least-32-bytes";

    @Test
    void applicationConfiguration_hasNoTrackedJwtSecretFallback() throws Exception {
        PropertySource<?> applicationProperties = new YamlPropertySourceLoader()
                .load("lab-application", new ClassPathResource("application.yml"))
                .stream()
                .findFirst()
                .orElseThrow();

        assertThat(applicationProperties.getProperty("mediflow.jwt.secret"))
                .isEqualTo("${MEDIFLOW_JWT_SECRET}");
    }

    @Test
    void applicationConfiguration_failsWhenJwtSecretIsMissing() {
        new ApplicationContextRunner()
                .withInitializer(productionConfigWithoutProcessEnvironment())
                .withUserConfiguration(JwtFilterConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()).getMessage())
                            .contains("MEDIFLOW_JWT_SECRET");
                });
    }

    @Test
    void applicationConfiguration_bindsProvidedJwtSecretAndCreatesJwtFilter() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(JwtFilterConfiguration.class)
                .withPropertyValues("MEDIFLOW_JWT_SECRET=" + VALID_SECRET)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtProperties.class);
                    assertThat(context).hasSingleBean(JwtAuthFilter.class);
                    assertThat(context.getBean(JwtProperties.class).secret())
                            .isEqualTo(VALID_SECRET);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    static class JwtFilterConfiguration {

        @Bean
        JwtAuthFilter jwtAuthFilter(JwtProperties properties) {
            return new JwtAuthFilter(properties);
        }
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root;
    }

    private static ApplicationContextInitializer<ConfigurableApplicationContext>
    productionConfigWithoutProcessEnvironment() {
        return context -> {
            new ConfigDataApplicationContextInitializer().initialize(context);
            ConfigurableEnvironment environment = context.getEnvironment();
            environment.getPropertySources().remove(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            environment.getPropertySources().remove(
                    StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        };
    }
}
