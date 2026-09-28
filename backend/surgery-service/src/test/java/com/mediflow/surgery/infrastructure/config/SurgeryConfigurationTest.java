package com.mediflow.surgery.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class SurgeryConfigurationTest {

    @Test
    void applicationConfiguration_usesSurgeryCoordinatesAndExternalSecrets() throws Exception {
        PropertySource<?> properties = applicationProperties();

        assertThat(properties.getProperty("server.port")).isEqualTo(8091);
        assertThat(properties.getProperty("spring.application.name")).isEqualTo("surgery-service");
        assertThat(properties.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://localhost:5432/mediflow_surgery");
        assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(properties.getProperty("spring.flyway.enabled")).isEqualTo(true);
        assertThat(properties.getProperty("mediflow.jwt.secret"))
                .isEqualTo("${MEDIFLOW_JWT_SECRET}");
        assertThat(properties.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,info");
    }

    @Test
    void applicationConfiguration_keepsBusinessAndMessageAdaptersDisabled() throws Exception {
        PropertySource<?> properties = applicationProperties();

        assertThat(properties.getProperty("mediflow.features.surgery.enabled")).isEqualTo(false);
        assertThat(properties.getProperty("mediflow.surgery.messaging.producer.enabled"))
                .isEqualTo(false);
        assertThat(properties.getProperty("mediflow.surgery.messaging.consumers.enabled"))
                .isEqualTo(false);
        assertThat(properties.getProperty("spring.cloud.openfeign.client.config.default.connectTimeout"))
                .isEqualTo(2000);
        assertThat(properties.getProperty("spring.cloud.openfeign.client.config.default.readTimeout"))
                .isEqualTo(3000);
    }

    private PropertySource<?> applicationProperties() throws Exception {
        return new YamlPropertySourceLoader()
                .load("surgery-application", new ClassPathResource("application.yml"))
                .stream()
                .findFirst()
                .orElseThrow();
    }
}
