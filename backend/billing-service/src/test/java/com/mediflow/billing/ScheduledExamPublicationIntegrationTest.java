package com.mediflow.billing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.event.OutpatientRegistrationChangedEvent;
import com.mediflow.billing.application.port.in.ConsumeOutpatientRegistrationFactUseCase;
import com.mediflow.billing.application.port.out.OutpatientQuoteRepositoryPort;
import com.mediflow.billing.application.port.out.PriceCatalogPort;
import com.mediflow.billing.application.port.out.PriceCatalogPort.PriceSnapshot;
import com.mediflow.billing.domain.exception.BillingRuleException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
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
/** Scheduled EXAM cash reaches Report: payment.completed passes the cohort fence instead of staying HELD. */
class ScheduledExamPublicationIntegrationTest {
    private static final String SECRET = "billing-scheduled-publication-secret-at-least-32-bytes";
    // Payment completes before the quote TTL; execution is observed after that TTL by PostgreSQL.
    private static final Instant CHANGED_AT = Instant.now().minusSeconds(3600)
            .truncatedTo(java.time.temporal.ChronoUnit.MICROS);

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
        registry.add("mediflow.billing.clearance-lookup.enabled", () -> true);
        registry.add("mediflow.billing.scheduled-payments.enabled", () -> true);
        registry.add("mediflow.billing.outpatient-quotes.enabled", () -> true);
        registry.add("mediflow.billing.zero-payable-eligibility.enabled", () -> true);
        registry.add("mediflow.billing.scheduled-eligibility-publication.enabled", () -> true);
        registry.add("mediflow.billing.scheduled-eligibility-publication.acceptance-reference", () -> "TEST-A10");
        registry.add("billing.outpatient-quotes.catalogue-version", () -> "CATALOGUE-TEST-1");
        registry.add("billing.outpatient-quotes.policy-version", () -> "POLICY-TEST-1");
        registry.add("billing.outpatient-quotes.items[0].source-type", () -> "EXAM");
        registry.add("billing.outpatient-quotes.items[0].price-code", () -> "OUTPATIENT_EXAM");
        registry.add("billing.outpatient-quotes.items[0].support-rate", () -> "0.5");
        registry.add("billing.outpatient-quotes.items[1].source-type", () -> "EXAM");
        registry.add("billing.outpatient-quotes.items[1].price-code", () -> "TEST_FULL_SUPPORT");
        registry.add("billing.outpatient-quotes.items[1].support-rate", () -> "1");
        registry.add("mediflow.billing.scheduled-payments.recovery-initial-delay-ms", () -> 3600000);
        registry.add("mediflow.billing.scheduled-payments.recovery-delay-ms", () -> 3600000);
        registry.add("mediflow.features.outpatient-mvp.enabled", () -> true);
        registry.add("mediflow.billing.outpatient-publication.enabled", () -> true);
        registry.add("mediflow.billing.outpatient-publication.purposes", () -> "EXAM");
        registry.add("mediflow.billing.outpatient-publication.acceptance-reference", () -> "TEST-SCHEDULED-EXAM");
        registry.add("mediflow.billing.outbox.enabled", () -> false);
        registry.add("mediflow.billing.outbox.metrics-enabled", () -> false);
        registry.add("mediflow.billing.outbox.maintenance-enabled", () -> false);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired ConsumeOutpatientRegistrationFactUseCase registrations;
    @Autowired OutpatientQuoteRepositoryPort quoteRepository;
    @MockBean PriceCatalogPort prices;
    @MockBean(name = "billingClock") Clock billingClock;
    @MockBean com.mediflow.billing.application.port.out.ClinicalPaymentLockPort clinical;
    @MockBean com.mediflow.billing.application.port.out.ClinicalPaymentContextPort paymentContexts;
    @Autowired com.mediflow.billing.application.service.ScheduledPaymentCoordinator payments;
    @Autowired com.mediflow.billing.application.port.out.FinancialClearanceAuthorityPort clearanceAuthority;
    @Autowired com.mediflow.billing.application.port.in.StartOutpatientExecutionUseCase executions;

    @BeforeEach void resetState() {
        jdbc.execute("TRUNCATE BILLING_ACCOUNT, BILLING_EVENT_OUTBOX CASCADE");
        when(billingClock.instant()).thenReturn(CHANGED_AT.plusSeconds(60));
        when(billingClock.getZone()).thenReturn(ZoneOffset.UTC);
        when(prices.requireActive("OUTPATIENT_EXAM", CHANGED_AT))
                .thenReturn(new PriceSnapshot("Outpatient visit", new BigDecimal("100000")));
        when(prices.requireActive("TEST_FULL_SUPPORT", CHANGED_AT))
                .thenReturn(new PriceSnapshot("Test full support", new BigDecimal("100000")));
        when(clinical.acquire(any())).thenAnswer(invocation -> {
            com.mediflow.billing.domain.model.ScheduledPaymentOperation op = invocation.getArgument(0);
            return new com.mediflow.billing.domain.model.ScheduledPaymentLease(op.paymentOperationId(), op.reservationId(),
                    2L, 1L, CHANGED_AT.plusSeconds(60), CHANGED_AT.plusSeconds(180),
                    op.basis(), op.quoteId());
        });
    }

    @Test void scheduledExamPaymentIsEnrolledAndPublishedThroughTheOutpatientFence() throws Exception {
        UUID registrationId = UUID.randomUUID();
        var event = event(registrationId, UUID.randomUUID(), "OUTPATIENT_EXAM");
        registrations.consume(event, mapper.writeValueAsBytes(event));
        UUID requestId = jdbc.queryForObject("SELECT payment_request_id FROM outpatient_registration_source WHERE registration_id=?",
                UUID.class, registrationId);
        var cohort = jdbc.queryForMap("SELECT purpose, source_id FROM billing_outpatient_publication_cohort WHERE payment_request_id=?",
                requestId);
        assertThat(cohort.get("purpose")).isEqualTo("EXAM");
        assertThat(cohort.get("source_id")).isEqualTo(registrationId);

        var quote = quoteRepository.findCurrent(requestId).orElseThrow();
        var command = new com.mediflow.billing.application.dto.command.ScheduledPaymentCommand(event.payload().reservationId(),
                1, quote.patientPayable(), "VND", com.mediflow.billing.domain.model.PaymentMethod.CASH, null, quote.quoteId(), "PATIENT_PAYMENT");
        var paid = payments.pay(requestId, command, UUID.randomUUID(), "scheduled-publication", "scheduled-publication",
                CHANGED_AT.plusSeconds(60));

        var outbox = jdbc.queryForMap("""
                SELECT payload, publication_enabled, outpatient_payment_request_id
                  FROM billing_event_outbox WHERE routing_key='payment.completed'
                """);
        assertThat(outbox.get("publication_enabled")).isEqualTo(true);
        assertThat(outbox.get("outpatient_payment_request_id")).isEqualTo(requestId);
        var payload = mapper.readTree((String) outbox.get("payload")).path("payload");
        assertThat(payload.path("transactionId").asText()).isEqualTo(paid.payment().transactionId().toString());
        assertThat(payload.path("prescriptionId").isNull()).isTrue();
        assertThat(payload.path("labTestIds")).isEmpty();
    }

    private static OutpatientRegistrationChangedEvent event(UUID registrationId, UUID reservationId, String priceCode) {
        UUID patientId = UUID.randomUUID();
        var fact = new OutpatientRegistrationChangedEvent.RegistrationFact(registrationId, 1, patientId,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.ofInstant(CHANGED_AT, ZoneOffset.UTC), LocalTime.of(9, 0), LocalTime.of(9, 30), "NEW_VISIT",
                reservationId, "HELD", "AWAITING_PAYMENT", null, null, CHANGED_AT.plusSeconds(20 * 60L),
                priceCode, "EXAM", registrationId, "OUTPATIENT_VISIT", registrationId, null, CHANGED_AT);
        return new OutpatientRegistrationChangedEvent(UUID.randomUUID(), "outpatient.registration.changed", 1,
                CHANGED_AT, "quote-trace", "clinical-service", fact);
    }
}
