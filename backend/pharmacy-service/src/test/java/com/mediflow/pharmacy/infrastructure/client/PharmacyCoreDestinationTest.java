package com.mediflow.pharmacy.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.io.ClassPathResource;

class PharmacyCoreDestinationTest {
    @Test
    void compatibilityKeepsOriginalDiscoveryNamesAndClientIdentities() {
        var environment = new MockEnvironment();
        assertThat(resolve(environment, PharmacyPatientFeignClient.class)).isEqualTo("patient-service");
        assertThat(resolve(environment, PharmacyOrganizationFeignClient.class)).isEqualTo("organization-service");
        assertThat(PharmacyPatientFeignClient.class.getAnnotation(FeignClient.class).contextId())
                .isEqualTo("pharmacyPatientIdentityClient");
        assertThat(PharmacyOrganizationFeignClient.class.getAnnotation(FeignClient.class).contextId())
                .isEqualTo("pharmacyOrganizationIdentityClient");
    }

    @Test
    void mvpProfileTargetsCoreWithoutChangingClinicalOrLogicalClientIdentity() throws Exception {
        var environment = new MockEnvironment();
        var sources = new YamlPropertySourceLoader().load("mvp-outpatient",
                new ClassPathResource("application-mvp-outpatient.yml"));
        sources.forEach(source -> environment.getPropertySources().addFirst(source));
        assertThat(resolve(environment, PharmacyPatientFeignClient.class)).isEqualTo("core-service");
        assertThat(resolve(environment, PharmacyOrganizationFeignClient.class)).isEqualTo("core-service");
        assertThat(resolve(environment, PharmacyClinicalFeignClient.class)).isEqualTo("clinical-service");
        assertThat(environment.getProperty("mediflow.pharmacy.outpatient-api.enabled", Boolean.class))
                .isFalse();
    }

    private String resolve(MockEnvironment environment, Class<?> client) {
        return environment.resolveRequiredPlaceholders(client.getAnnotation(FeignClient.class).name());
    }
}
