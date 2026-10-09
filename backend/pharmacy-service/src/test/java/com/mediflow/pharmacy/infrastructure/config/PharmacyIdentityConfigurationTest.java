package com.mediflow.pharmacy.infrastructure.config;

import com.mediflow.pharmacy.application.port.out.PrescriptionIdentityPort;
import com.mediflow.pharmacy.infrastructure.client.PharmacyOrganizationFeignClient;
import com.mediflow.pharmacy.infrastructure.client.PharmacyPatientFeignClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class PharmacyIdentityConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(PharmacyIdentityConfiguration.class);
    @ParameterizedTest @ValueSource(ints = {0, 1, 2})
    void configuration_requiresBothGatesWithoutDormantFeignClients(int flags) {
        runner.withPropertyValues("mediflow.features.care-finance-v2=" + ((flags & 1) != 0),
                "mediflow.pharmacy.identity.enabled=" + ((flags & 2) != 0)).run(context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(PrescriptionIdentityPort.class)
                        .doesNotHaveBean(PharmacyPatientFeignClient.class).doesNotHaveBean(PharmacyOrganizationFeignClient.class));
    }
    @Test void configuration_missingFlagsIsDisabled() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(PrescriptionIdentityPort.class));
    }
}
