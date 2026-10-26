package com.mediflow.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.event.OutpatientRegistrationChangedEvent;
import com.mediflow.billing.application.port.in.ConsumeOutpatientRegistrationFactUseCase;
import com.mediflow.billing.application.port.out.FinancialClearanceAuthorityPort;
import com.mediflow.billing.application.port.out.OutpatientQuoteRepositoryPort;
import com.mediflow.billing.application.port.out.PriceCatalogPort;
import com.mediflow.billing.application.port.out.PriceCatalogPort.PriceSnapshot;
import com.mediflow.billing.domain.model.ScheduledPaymentLease;
import com.mediflow.billing.domain.model.ScheduledPaymentOperation;
import com.mediflow.billing.application.port.out.ClinicalPaymentLockPort;
import com.mediflow.billing.application.service.ScheduledPaymentCoordinator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class FinancialClearanceZeroAuthorityIntegrationTest {
    private static final String SECRET = "billing-zero-authority-integration-secret-at-least-32-bytes";
    private static final Instant CHANGED_AT = Instant.parse("2026-10-26T01:00:00Z");

    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> false);
        registry.add("mediflow.jwt.secret", () -> SECRET);
        registry.add("eureka.client.enabled", () -> false);
        registry.add("mediflow.billing.ledger.enabled", () -> true);
        registry.add("mediflow.billing.scheduled-payments.enabled", () -> true);
        registry.add("mediflow.billing.outpatient-quotes.enabled", () -> true);
        registry.add("mediflow.billing.zero-payable-eligibility.enabled", () -> true);
        registry.add("billing.outpatient-quotes.catalogue-version", () -> "CATALOGUE-TEST-1");
        registry.add("billing.outpatient-quotes.policy-version", () -> "POLICY-TEST-1");
        registry.add("billing.outpatient-quotes.items[0].source-type", () -> "EXAM");
        registry.add("billing.outpatient-quotes.items[0].price-code", () -> "TEST_FULL_SUPPORT");
        registry.add("billing.outpatient-quotes.items[0].support-rate", () -> "1");
        registry.add("mediflow.billing.scheduled-payments.recovery-initial-delay-ms", () -> 3600000);
        registry.add("mediflow.billing.scheduled-payments.recovery-delay-ms", () -> 3600000);
        registry.add("mediflow.billing.outbox.enabled", () -> false);
        registry.add("mediflow.billing.outbox.metrics-enabled", () -> false);
        registry.add("mediflow.billing.outbox.maintenance-enabled", () -> false);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired ConsumeOutpatientRegistrationFactUseCase registrations;
    @Autowired FinancialClearanceAuthorityPort clearances;
    @Autowired OutpatientQuoteRepositoryPort quotes;
    @Autowired ScheduledPaymentCoordinator payments;
    @MockBean PriceCatalogPort prices;
    @MockBean ClinicalPaymentLockPort clinical;
    @MockBean com.mediflow.billing.application.port.out.ClinicalPaymentContextPort paymentContexts;

    @BeforeEach void resetState() {
        jdbc.execute("TRUNCATE BILLING_ACCOUNT, BILLING_EVENT_OUTBOX CASCADE");
        when(prices.requireActive("TEST_FULL_SUPPORT", CHANGED_AT))
                .thenReturn(new PriceSnapshot("Full support exam", new BigDecimal("100000")));
        when(clinical.acquire(any())).thenAnswer(invocation -> {
            ScheduledPaymentOperation operation = invocation.getArgument(0);
            return new ScheduledPaymentLease(operation.paymentOperationId(), operation.reservationId(),
                    2L, 1L, CHANGED_AT.plusSeconds(60), CHANGED_AT.plusSeconds(180),
                    operation.basis(), operation.quoteId());
        });
    }

    @Test void outpatientExamZeroLiabilityReceiptIsValidClearanceAuthorityWithoutCash() throws Exception {
        var event = event();
        registrations.consume(event, mapper.writeValueAsBytes(event));
        UUID paymentRequestId = jdbc.queryForObject(
                "SELECT payment_request_id FROM outpatient_registration_source WHERE registration_id=?",
                UUID.class, event.payload().registrationId());
        var quote = quotes.findCurrent(paymentRequestId).orElseThrow();
        var command = new com.mediflow.billing.application.dto.command.ScheduledPaymentCommand(
                event.payload().reservationId(), 1, BigDecimal.ZERO, "VND",
                com.mediflow.billing.domain.model.PaymentMethod.CASH, null, quote.quoteId(), "ZERO_LIABILITY");
        var result = payments.pay(paymentRequestId, command, UUID.randomUUID(), "zero-authority", "zero-authority-trace",
                CHANGED_AT.plusSeconds(60));

        // The adapter previously rejected zero because its shared query required requested_amount > 0.
        assertThat(clearances.find(result.clearanceId()).orElseThrow().ledgerEligible()).isTrue();
    }

    private static OutpatientRegistrationChangedEvent event() {
        UUID registrationId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        var fact = new OutpatientRegistrationChangedEvent.RegistrationFact(registrationId, 1, patientId,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 10, 26), LocalTime.of(9, 0), LocalTime.of(9, 30), "NEW_VISIT",
                reservationId, "HELD", "AWAITING_PAYMENT", null, null, CHANGED_AT.plusSeconds(20 * 60L),
                "TEST_FULL_SUPPORT", "EXAM", registrationId, "OUTPATIENT_VISIT", registrationId, null, CHANGED_AT);
        return new OutpatientRegistrationChangedEvent(UUID.randomUUID(), "outpatient.registration.changed", 1,
                CHANGED_AT, "zero-authority-trace", "clinical-service", fact);
    }
}
