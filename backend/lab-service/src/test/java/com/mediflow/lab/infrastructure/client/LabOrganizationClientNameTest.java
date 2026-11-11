package com.mediflow.lab.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/** Organization runs inside core-service; no Eureka instance is named organization-service. */
class LabOrganizationClientNameTest {

    private static Properties yaml(String file) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(file));
        return factory.getObject();
    }

    @Test
    void outpatientProfileCallsOrganizationThroughCoreService() {
        assertThat(yaml("application-outpatient-mvp.yml").getProperty("mediflow.clients.organization-service"))
                .isEqualTo("core-service");
    }

    @Test
    void baseConfigurationLetsTheDeploymentNameTheOrganizationHost() {
        assertThat(yaml("application.yml").getProperty("mediflow.clients.organization-service"))
                .isEqualTo("${MEDIFLOW_ORGANIZATION_SERVICE:organization-service}");
    }
}
