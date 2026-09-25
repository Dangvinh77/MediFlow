package com.mediflow.inpatient.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class InpatientConfigurationTest {

    @Test
    void applicationConfiguration_usesFoundationCoordinatesAndExternalJwtSecret() throws Exception {
        PropertySource<?> applicationProperties = new YamlPropertySourceLoader()
                .load("inpatient-application", new ClassPathResource("application.yml"))
                .stream()
                .findFirst()
                .orElseThrow();

        assertThat(applicationProperties.getProperty("server.port")).isEqualTo(8090);
        assertThat(applicationProperties.getProperty("spring.application.name"))
                .isEqualTo("inpatient-service");
        assertThat(applicationProperties.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://localhost:5432/mediflow_inpatient");
        assertThat(applicationProperties.getProperty("spring.jpa.hibernate.ddl-auto"))
                .isEqualTo("validate");
        assertThat(applicationProperties.getProperty("spring.flyway.enabled")).isEqualTo(true);
        assertThat(applicationProperties.getProperty("mediflow.jwt.secret"))
                .isEqualTo("${MEDIFLOW_JWT_SECRET}");
        assertThat(applicationProperties.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,info");
    }
}
