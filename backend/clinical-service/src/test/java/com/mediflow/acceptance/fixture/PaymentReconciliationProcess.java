package com.mediflow.acceptance.fixture;

import com.mediflow.clinical.ClinicalServiceApplication;
import com.mediflow.clinical.application.dto.authority.ScheduledPaymentSnapshot;
import com.mediflow.clinical.application.exception.UpstreamUnavailableException;
import com.mediflow.clinical.application.port.out.ScheduledPaymentReconciliationPort;
import com.mediflow.clinical.application.service.OutpatientPaymentRecoveryWorker;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

/** Fresh Clinical JVM, real recovery/store/transactions; Billing responses and time are test fixtures. */
public final class PaymentReconciliationProcess {
    private static String phase;
    private static Instant now;
    private static UUID requestId;

    public static void main(String[] args) {
        phase = args[0];
        now = Instant.parse(args[1]);
        requestId = UUID.fromString(args[2]);
        try (var context = new SpringApplicationBuilder(ClinicalServiceApplication.class, RecoveryFixture.class)
                .web(WebApplicationType.SERVLET).run(
                        "--server.port=0", "--eureka.client.enabled=false",
                        "--spring.main.allow-bean-definition-overriding=true",
                        "--spring.datasource.url=" + System.getenv("TEST_DB_URL"),
                        "--spring.datasource.username=" + System.getenv("TEST_DB_USER"),
                        "--spring.datasource.password=" + System.getenv("TEST_DB_PASSWORD"),
                        "--spring.rabbitmq.listener.simple.auto-startup=false",
                        "--spring.rabbitmq.listener.direct.auto-startup=false",
                        "--mediflow.jwt.secret=reconciliation-process-test-secret-at-least-32-bytes",
                        "--mediflow.clinical.scheduling.enabled=true",
                        "--mediflow.clinical.outbox.enabled=false")) {
            context.getBean(OutpatientPaymentRecoveryWorker.class).runOnce("process-recovery-test");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class RecoveryFixture {
        @Bean(name = "clinicalSchedulingClock")
        Clock recoveryClock() {
            return Clock.fixed(now, ZoneOffset.UTC);
        }

        // Invoke one real worker pass explicitly, so startup polling cannot consume its claim first.
        @Bean
        static BeanFactoryPostProcessor manualPollingOnly() {
            return factory -> ((BeanDefinitionRegistry) factory).removeBeanDefinition(
                    TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME);
        }

        @Bean
        @Primary
        ScheduledPaymentReconciliationPort billingFixture() {
            return new ScheduledPaymentReconciliationPort() {
                @Override
                public ScheduledPaymentSnapshot lookup(UUID operation, UUID registration, UUID reservation, String correlation) {
                    if ("MISSING".equals(phase)) throw new UpstreamUnavailableException("BILLING_NOT_FOUND", "Missing operation fixture");
                    return snapshot(operation, registration, reservation, "PREPARING");
                }

                @Override
                public ScheduledPaymentSnapshot closeIfUnpaid(UUID operation, UUID registration, UUID reservation,
                        long fence, String reason, String correlation) {
                    return snapshot(operation, registration, reservation, "CLOSED_NOT_PAID");
                }

                private ScheduledPaymentSnapshot snapshot(UUID operation, UUID registration, UUID reservation, String status) {
                    return new ScheduledPaymentSnapshot(new ScheduledPaymentSnapshot.Operation(operation, requestId,
                            registration, reservation, status, null, "100.00", "VND", now.minusSeconds(180), now,
                            "PATIENT_PAYMENT", null, null), null, null);
                }
            };
        }
    }
}
