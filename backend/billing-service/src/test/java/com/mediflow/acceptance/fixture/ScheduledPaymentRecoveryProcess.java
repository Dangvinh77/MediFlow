package com.mediflow.acceptance.fixture;

import com.mediflow.billing.BillingServiceApplication;
import com.mediflow.billing.application.dto.command.ScheduledPaymentCommand;
import com.mediflow.billing.application.port.out.ClinicalPaymentLockPort;
import com.mediflow.billing.application.service.ScheduledPaymentCoordinator;
import com.mediflow.billing.domain.model.PaymentMethod;
import com.mediflow.billing.domain.model.ScheduledPaymentLease;
import com.mediflow.billing.domain.model.ScheduledPaymentOperation;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Separate JVM crash harness. Billing is real; only the remote Clinical authority is a fixture. */
public final class ScheduledPaymentRecoveryProcess {
    private static String phase;
    private static Path receipt;

    public static void main(String[] args) throws Exception {
        phase = args[0];
        receipt = Path.of(args[1]);
        try (var context = new SpringApplicationBuilder(BillingServiceApplication.class, AuthorityFixture.class)
                .web(WebApplicationType.SERVLET).run(
                        "--server.port=0",
                        "--spring.datasource.url=" + System.getenv("TEST_DB_URL"),
                        "--spring.datasource.username=" + System.getenv("TEST_DB_USER"),
                        "--spring.datasource.password=" + System.getenv("TEST_DB_PASSWORD"),
                        "--spring.rabbitmq.host=" + System.getenv("TEST_RABBIT_HOST"),
                        "--spring.rabbitmq.port=" + System.getenv("TEST_RABBIT_PORT"),
                        "--spring.rabbitmq.username=" + System.getenv("TEST_RABBIT_USER"),
                        "--spring.rabbitmq.password=" + System.getenv("TEST_RABBIT_PASSWORD"),
                        "--spring.rabbitmq.listener.simple.auto-startup=false",
                        "--eureka.client.enabled=false",
                        "--mediflow.jwt.secret=process-recovery-test-secret-at-least-32-bytes",
                        "--mediflow.billing.ledger.enabled=true",
                        "--mediflow.billing.scheduled-payments.enabled=true",
                        "--mediflow.billing.scheduled-payments.recovery-initial-delay-ms=3600000",
                        "--mediflow.billing.outbox.enabled=false",
                        "--mediflow.billing.outbox.metrics-enabled=false",
                        "--mediflow.billing.outbox.maintenance-enabled=false")) {
            var coordinator = context.getBean(ScheduledPaymentCoordinator.class);
            if ("RECOVER".equals(phase)) {
                coordinator.recoverPending(Instant.now(), 10);
            } else {
                coordinator.pay(UUID.fromString(args[2]), new ScheduledPaymentCommand(
                        UUID.fromString(args[3]), 5, new BigDecimal("100"), "VND", PaymentMethod.CASH, null),
                        UUID.fromString(args[4]), "process-crash-key", "process-crash-trace", Instant.now());
                throw new IllegalStateException("Expected parent to kill the child at the crash barrier");
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AuthorityFixture {
        @Bean
        @Primary
        ClinicalPaymentLockPort crashBarrierAuthority() {
            return new ClinicalPaymentLockPort() {
                @Override
                public ScheduledPaymentLease acquire(ScheduledPaymentOperation operation) {
                    if ("BEFORE_ACQUIRE".equals(phase)) barrier(operation.paymentOperationId().toString());
                    Instant at = Instant.now().truncatedTo(ChronoUnit.MICROS);
                    return new ScheduledPaymentLease(operation.paymentOperationId(), operation.reservationId(),
                            operation.expectedReservationRevision() + 1, 41, at, at.plusSeconds(120));
                }

                @Override
                public void resolve(PaymentResolution resolution) {
                    String value = resolution.paymentOperationId() + ":" + resolution.transactionId()
                            + ":" + resolution.clearanceId();
                    if ("AFTER_COMMIT".equals(phase)) barrier(value);
                    else write(value);
                }
            };
        }
    }

    private static void barrier(String value) {
        write(value);
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static void write(String value) {
        try {
            Path pending = receipt.resolveSibling(receipt.getFileName() + ".tmp");
            Files.writeString(pending, value);
            Files.move(pending, receipt, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
