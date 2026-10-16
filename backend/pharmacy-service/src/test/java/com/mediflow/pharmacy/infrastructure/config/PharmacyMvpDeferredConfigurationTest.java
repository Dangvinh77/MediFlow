package com.mediflow.pharmacy.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.pharmacy.application.port.out.AdmissionAuthorityPort;
import com.mediflow.pharmacy.infrastructure.client.PharmacyInpatientFeignClient;
import com.mediflow.pharmacy.messaging.consumer.AdmissionLifecycleConsumer;
import com.mediflow.pharmacy.messaging.consumer.PaymentCompletedConsumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PharmacyMvpDeferredConfigurationTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mvpExcludesAdmissionEvenWhenEveryLegacyGateIsEnabled(boolean global) {
        new ApplicationContextRunner()
                .withUserConfiguration(PharmacyAdmissionConsumerConfiguration.class,
                        PharmacyAdmissionAuthorityConfiguration.class, PaymentCompletedConsumer.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("mvp-outpatient", "dev"))
                .withPropertyValues("mediflow.features.care-finance-v2=" + global,
                        "mediflow.pharmacy.admission-consumer.enabled=true",
                        "mediflow.pharmacy.admission-authority.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(PharmacyAdmissionConsumerConfiguration.class);
                    assertThat(context).doesNotHaveBean(PharmacyAdmissionAuthorityConfiguration.class);
                    assertThat(context).doesNotHaveBean(AdmissionLifecycleConsumer.class);
                    assertThat(context).doesNotHaveBean(AdmissionAuthorityPort.class);
                    assertThat(context).doesNotHaveBean(PharmacyInpatientFeignClient.class);
                    assertThat(context).doesNotHaveBean(PaymentCompletedConsumer.class);
                    assertThat(context).doesNotHaveBean("pharmacyAdmissionTopology");
                    assertThat(context).doesNotHaveBean("pharmacyAdmissionListenerFactory");
                });
    }
}
