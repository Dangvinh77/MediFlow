package com.mediflow.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mediflow.billing.infrastructure.config.OutpatientRefundPublicationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class OutpatientRefundPublicationPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Properties.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OutpatientRefundPublicationProperties.class)
    static class Properties {}

    @Test void defaultIsOff() {
        runner.run(context -> assertThat(context.getBean(OutpatientRefundPublicationProperties.class).enabled()).isFalse());
    }

    @Test void approvalReferenceIsRequiredWhenEnabled() {
        runner.withPropertyValues("mediflow.billing.outpatient-refund-publication.enabled=true")
                .run(context -> assertThat(context).hasFailed());
        assertThatThrownBy(() -> new OutpatientRefundPublicationProperties(true, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutpatientRefundPublicationProperties(true, "x".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void explicitGateBindsOnlyWithApprovalReference() {
        runner.withPropertyValues("mediflow.billing.outpatient-refund-publication.enabled=true",
                        "mediflow.billing.outpatient-refund-publication.acceptance-reference=reviewed-disposable-run")
                .run(context -> {
                    var properties = context.getBean(OutpatientRefundPublicationProperties.class);
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.acceptanceReference()).isEqualTo("reviewed-disposable-run");
                });
    }
}
