package com.mediflow.pharmacy.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import com.mediflow.pharmacy.infrastructure.client.PharmacyOrganizationFeignClient;
import com.mediflow.pharmacy.infrastructure.client.PharmacyPatientFeignClient;
import com.mediflow.pharmacy.messaging.consumer.PaymentCompletedConsumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.FeignClient;

class PharmacyMvpProfileAliasTest {
    @ParameterizedTest
    @ValueSource(strings = {"outpatient-mvp", "mvp-outpatient"})
    void aliasesLoadCoreAndExcludeDeferredConsumersDespiteGlobalOverrides(String profile) {
        new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(PharmacyAdmissionAuthorityConfiguration.class,
                        PharmacyAdmissionConsumerConfiguration.class, PaymentCompletedConsumer.class)
                .withPropertyValues("spring.profiles.active=" + profile,
                        "mediflow.features.care-finance-v2=true",
                        "mediflow.pharmacy.admission-authority.enabled=true",
                        "mediflow.pharmacy.admission-consumer.enabled=true")
                .run(c -> {
                    assertThat(c).hasNotFailed().doesNotHaveBean(PaymentCompletedConsumer.class)
                            .doesNotHaveBean(PharmacyAdmissionAuthorityConfiguration.class)
                            .doesNotHaveBean(PharmacyAdmissionConsumerConfiguration.class);
                    for (var client : java.util.List.of(PharmacyPatientFeignClient.class, PharmacyOrganizationFeignClient.class)) {
                        assertThat(c.getEnvironment().resolveRequiredPlaceholders(client.getAnnotation(FeignClient.class).name()))
                                .isEqualTo("core-service");
                    }
                    assertThat(c.getEnvironment().getProperty("mediflow.pharmacy.outpatient-api.enabled", Boolean.class)).isFalse();
                });
    }
}
