package com.mediflow.pharmacy.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.application.port.out.AdmissionAuthorityPort;
import com.mediflow.pharmacy.infrastructure.client.PharmacyInpatientFeignClient;
import com.mediflow.pharmacy.infrastructure.security.JwtProperties;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class PharmacyAdmissionAuthorityConfigurationTest {
    @ParameterizedTest @ValueSource(ints = {0, 1, 2})
    void lookup_requiresBothGatesAndRegistersNoFeignClientWhenDisabled(int flags) {
        runner().withPropertyValues("mediflow.features.care-finance-v2=" + ((flags & 1) != 0),
                "mediflow.pharmacy.admission-authority.enabled=" + ((flags & 2) != 0)).run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(AdmissionAuthorityPort.class)
                    .doesNotHaveBean(PharmacyInpatientFeignClient.class);
        });
    }
    @Test void lookup_missingPropertiesIsDisabled() {
        runner().run(context -> assertThat(context).doesNotHaveBean(AdmissionAuthorityPort.class)
                .doesNotHaveBean(PharmacyInpatientFeignClient.class));
    }
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(PharmacyAdmissionAuthorityConfiguration.class)
                .withBean(Clock.class, Clock::systemUTC).withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(JwtProperties.class, () -> new JwtProperties("authority-config-test-secret-at-least-32-bytes"));
    }
}
