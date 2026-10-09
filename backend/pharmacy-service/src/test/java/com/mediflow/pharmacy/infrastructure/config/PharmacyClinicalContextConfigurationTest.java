package com.mediflow.pharmacy.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.application.port.out.OutpatientPrescriptionContextPort;
import com.mediflow.pharmacy.infrastructure.client.PharmacyClinicalFeignClient;
import com.mediflow.pharmacy.infrastructure.security.JwtProperties;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class PharmacyClinicalContextConfigurationTest {
    @ParameterizedTest @ValueSource(ints = {0, 1, 2})
    void configuration_requiresBothGatesWithoutRegisteringDormantClient(int flags) {
        runner().withPropertyValues("mediflow.features.care-finance-v2=" + ((flags & 1) != 0),
                "mediflow.pharmacy.clinical-context.enabled=" + ((flags & 2) != 0)).run(context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(OutpatientPrescriptionContextPort.class).doesNotHaveBean(PharmacyClinicalFeignClient.class));
    }
    @Test void configuration_missingFlagsIsDisabled() {
        runner().run(context -> assertThat(context).doesNotHaveBean(OutpatientPrescriptionContextPort.class).doesNotHaveBean(PharmacyClinicalFeignClient.class));
    }
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(PharmacyClinicalContextConfiguration.class)
                .withBean(Clock.class, Clock::systemUTC).withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(JwtProperties.class, () -> new JwtProperties("pharmacy-config-test-secret-at-least-32-bytes"));
    }
}
