package com.mediflow.lab.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/** The read decision blocks a request thread, so it must not inherit Feign's 60 second default. */
class ClinicalReadDecisionClientTimeoutTest {
    @Test
    void readDecisionClientHasBoundedTimeouts() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties properties = yaml.getObject();
        String prefix = "spring.cloud.openfeign.client.config.labClinicalReadDecisionClient.";

        assertThat(properties.get(prefix + "connectTimeout")).isEqualTo(1000);
        // Clinical itself waits up to 3 seconds on Core before it can answer.
        assertThat(properties.get(prefix + "readTimeout")).isEqualTo(4000);
    }
}
