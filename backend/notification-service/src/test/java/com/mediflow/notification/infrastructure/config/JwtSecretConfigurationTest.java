package com.mediflow.notification.infrastructure.config;

import com.mediflow.notification.infrastructure.security.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSecretConfigurationTest {

    private static final String EXTERNAL_SECRET = "configured-test-secret-that-is-at-least-32-bytes";

    private WebApplicationContextRunner runner() {
        return new WebApplicationContextRunner()
                .withInitializer(JwtSecretConfigurationTest::removeHostConfiguration)
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(
                        JacksonAutoConfiguration.class,
                        SecurityAutoConfiguration.class,
                        SecurityFilterAutoConfiguration.class,
                        WebMvcAutoConfiguration.class))
                .withUserConfiguration(SecurityConfig.class);
    }

    @Test
    void refusesToCreateJwtAuthenticationWhenNoExternalSecretIsConfigured() {
        runner().run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .hasStackTraceContaining("JwtProperties")
                    .hasStackTraceContaining("mediflow.jwt.secret");
        });
    }

    @Test
    void createsJwtAuthenticationWhenExternalSecretIsConfigured() {
        runner().withPropertyValues("MEDIFLOW_JWT_SECRET=" + EXTERNAL_SECRET).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("spring.application.name"))
                    .isEqualTo("notification-service");
            assertThat(context).hasBean("notificationJwtAuthFilter");
            assertThat(context).hasSingleBean(SecurityFilterChain.class);
            assertThat(context.getBean(JwtProperties.class).secret()).isEqualTo(EXTERNAL_SECRET);
        });
    }

    private static void removeHostConfiguration(ConfigurableApplicationContext context) {
        context.getEnvironment().getPropertySources()
                .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        context.getEnvironment().getPropertySources()
                .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    }
}
