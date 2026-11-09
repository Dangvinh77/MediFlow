package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

class ClinicIntakeSettingsTest {
    @Test void defaultsToDisabled() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("src/main/resources/application.yml"));
        assertThat(yaml.getObject().getProperty("mediflow.v2.clinic-intake"))
                .isEqualTo("${MEDIFLOW_V2_CLINIC_INTAKE:false}");
        assertThat(new ClinicIntakeSettings(false).enabled()).isFalse();
        assertThat(new ClinicIntakeSettings(true).enabled()).isTrue();
    }
}
