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
class OutpatientQuoteIntegrationTest {
    private static final String SECRET = "billing-quote-integration-secret-at-least-32-bytes";
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

    @Test void registrationIssuancePersistsGrossQuoteAndPatientLiabilityAtomicallyAndReplaysExactEnvelope() throws Exception {
        UUID registrationId = UUID.randomUUID();
        var event = event(registrationId, UUID.randomUUID());
        byte[] raw = mapper.writeValueAsBytes(event);

        registrations.consume(event, raw);

        UUID requestId = jdbc.queryForObject("SELECT payment_request_id FROM outpatient_registration_source WHERE registration_id=?",
                UUID.class, registrationId);
        UUID quoteId = quoteRepository.findCurrent(requestId).orElseThrow().quoteId();
        var quote = quoteRepository.findCurrent(requestId).orElseThrow();
        assertThat(quote.patientId()).isEqualTo(event.payload().patientId());
        assertThat(quote.careEpisodeId()).isEqualTo(registrationId);
        assertThat(quote.grossAmount()).isEqualByComparingTo("100000");
        assertThat(quote.supportAmount()).isEqualByComparingTo("50000");
        assertThat(quote.patientPayable()).isEqualByComparingTo("50000");
        assertThat(quote.expiresAt()).isEqualTo(CHANGED_AT.plusSeconds(15 * 60L));
        assertThat(jdbc.queryForObject("SELECT requested_amount FROM PAYMENT_REQUEST WHERE payment_request_id=?",
                BigDecimal.class, requestId)).isEqualByComparingTo("50000");
        var charge = jdbc.queryForMap("""
                SELECT c.quantity,c.unit_amount,c.gross_amount,r.requested_amount
                  FROM PAYMENT_REQUEST_CHARGE r JOIN CHARGE c ON c.charge_id=r.charge_id
                 WHERE r.payment_request_id=?
                """, requestId);
        assertThat((BigDecimal) charge.get("quantity")).isEqualByComparingTo("1");
        assertThat((BigDecimal) charge.get("unit_amount")).isEqualByComparingTo("100000");
        assertThat((BigDecimal) charge.get("gross_amount")).isEqualByComparingTo("100000");
        assertThat((BigDecimal) charge.get("requested_amount")).isEqualByComparingTo("50000");
        assertThat(jdbc.queryForObject("SELECT quote_id FROM outpatient_current_quote WHERE payment_request_id=?",
                UUID.class, requestId)).isEqualTo(quoteId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outpatient_quote WHERE payment_request_id=?", Integer.class,
                requestId)).isOne();

        registrations.consume(event, raw);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outpatient_quote WHERE payment_request_id=?", Integer.class,
                requestId)).isOne();
        byte[] changedEnvelope = java.util.Arrays.copyOf(raw, raw.length + 1);
        changedEnvelope[changedEnvelope.length - 1] = (byte) ' ';
        assertThatThrownBy(() -> registrations.consume(event, changedEnvelope))
                .isInstanceOf(BillingRuleException.class)
                .hasMessageContaining("BILLING_REGISTRATION_EVENT_CONFLICT");
        verify(prices).requireActive("OUTPATIENT_EXAM", CHANGED_AT);
    }

    private static OutpatientRegistrationChangedEvent event(UUID registrationId, UUID reservationId) {
        return event(registrationId, reservationId, "OUTPATIENT_EXAM");
    }

    @Test void positivePaymentBindsQuoteAndReplayCreatesExactlyOneCashReceipt() throws Exception {
        var event = event(UUID.randomUUID(), UUID.randomUUID());
        registrations.consume(event, mapper.writeValueAsBytes(event));
        UUID requestId = jdbc.queryForObject("SELECT payment_request_id FROM outpatient_registration_source WHERE registration_id=?",
                UUID.class, event.payload().registrationId());
        var quote = quoteRepository.findCurrent(requestId).orElseThrow();
        var command = new com.mediflow.billing.application.dto.command.ScheduledPaymentCommand(event.payload().reservationId(),
                1, quote.patientPayable(), "VND", com.mediflow.billing.domain.model.PaymentMethod.CASH, null, quote.quoteId(), "PATIENT_PAYMENT");
        UUID actor = UUID.randomUUID();
        var first = payments.pay(requestId, command, actor, "quote-positive", "quote-payment", CHANGED_AT.plusSeconds(60));
        var replay = payments.pay(requestId, command, actor, "quote-positive", "quote-payment", CHANGED_AT.plusSeconds(61));
        assertThat(first.operation().quoteId()).isEqualTo(quote.quoteId());
        assertThat(replay.operation().paymentOperationId()).isEqualTo(first.operation().paymentOperationId());
        assertThat(first.payment().amount()).isEqualTo("50000.00");
        assertExecutable(first.clearanceId(), event);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_transaction WHERE payment_request_id=?", Integer.class, requestId)).isOne();
        var publication = jdbc.queryForMap("""
                SELECT payload,publication_enabled,scheduling_payment_operation_id
                  FROM billing_event_outbox WHERE routing_key='outpatient.financial.eligibility.granted'
                """);
        assertThat(publication.get("publication_enabled")).isEqualTo(true);
        assertThat(publication.get("scheduling_payment_operation_id")).isEqualTo(first.operation().paymentOperationId());
        var fact = mapper.readTree((String) publication.get("payload")).path("payload");
        assertThat(fact.path("basis").asText()).isEqualTo("PATIENT_PAYMENT");
        assertThat(fact.path("quoteId").asText()).isEqualTo(quote.quoteId().toString());
        assertThat(fact.path("transactionId").asText()).isEqualTo(first.payment().transactionId().toString());
        verify(clinical).acquire(any());

        var tampered = mapper.readTree((String) publication.get("payload")).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) tampered.path("payload")).put("quoteId", UUID.randomUUID().toString());
        var finalTamperedPayload = tampered.toString();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO BILLING_EVENT_OUTBOX(event_id,routing_key,aggregate_id,payload,publication_enabled,
                    contract_version,scheduling_payment_operation_id,scheduling_acceptance_reference)
                VALUES (?,?,?,?::jsonb,TRUE,1,?, 'TEST-TAMPER')
                """, UUID.randomUUID(), "outpatient.financial.eligibility.granted", event.payload().registrationId(),
                finalTamperedPayload, first.operation().paymentOperationId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void zeroLiabilityCreatesClearanceWithoutCashAndReplaysOriginalCompletionTime() throws Exception {
        var event = event(UUID.randomUUID(), UUID.randomUUID(), "TEST_FULL_SUPPORT");
        registrations.consume(event, mapper.writeValueAsBytes(event));
        UUID requestId = jdbc.queryForObject("SELECT payment_request_id FROM outpatient_registration_source WHERE registration_id=?",
                UUID.class, event.payload().registrationId());
        var quote = quoteRepository.findCurrent(requestId).orElseThrow();
        assertThat(quote.patientPayable()).isZero();
        var command = new com.mediflow.billing.application.dto.command.ScheduledPaymentCommand(event.payload().reservationId(),
                1, BigDecimal.ZERO, "VND", com.mediflow.billing.domain.model.PaymentMethod.CASH, null, quote.quoteId(), "ZERO_LIABILITY");
        UUID actor = UUID.randomUUID();
        var first = payments.pay(requestId, command, actor, "quote-zero", "zero-trace", CHANGED_AT.plusSeconds(60));
        var replay = payments.pay(requestId, command, actor, "quote-zero", "zero-trace", CHANGED_AT.plusSeconds(600));
        assertThat(first.payment()).isNull();
        assertThat(first.operation().transactionId()).isNull();
        assertThat(first.operation().basis()).isEqualTo("ZERO_LIABILITY");
        assertThat(first.operation().completedAt()).isEqualTo(CHANGED_AT.plusSeconds(60));
        assertThat(replay.operation().completedAt()).isEqualTo(first.operation().completedAt());
        assertThat(replay.clearanceId()).isEqualTo(first.clearanceId());
        assertExecutable(first.clearanceId(), event);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_transaction WHERE payment_request_id=?", Integer.class, requestId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_allocation a JOIN payment_transaction t ON t.transaction_id=a.transaction_id WHERE t.payment_request_id=?", Integer.class, requestId)).isZero();
        assertThat(jdbc.queryForObject("SELECT payment_method FROM financial_clearance WHERE clearance_id=?", String.class, first.clearanceId())).isEqualTo("ZERO_LIABILITY");
        assertThat(jdbc.queryForObject("SELECT status FROM payment_request WHERE payment_request_id=?", String.class, requestId)).isEqualTo("PAID");
        var publication = jdbc.queryForMap("""
                SELECT payload,publication_enabled,scheduling_payment_operation_id
                  FROM billing_event_outbox WHERE routing_key='outpatient.financial.eligibility.granted'
                """);
        assertThat(publication.get("publication_enabled")).isEqualTo(true);
        assertThat(publication.get("scheduling_payment_operation_id")).isEqualTo(first.operation().paymentOperationId());
        var fact = mapper.readTree((String) publication.get("payload")).path("payload");
        assertThat(fact.path("basis").asText()).isEqualTo("ZERO_LIABILITY");
        assertThat(fact.path("quoteId").asText()).isEqualTo(quote.quoteId().toString());
        assertThat(fact.path("transactionId").isNull()).isTrue();
    }

    @Test void staleOrExpiredQuoteCannotCreatePreparingOperationOrCash() throws Exception {
        var event = event(UUID.randomUUID(), UUID.randomUUID());
        registrations.consume(event, mapper.writeValueAsBytes(event));
        UUID requestId = jdbc.queryForObject("SELECT payment_request_id FROM outpatient_registration_source WHERE registration_id=?",
                UUID.class, event.payload().registrationId());
        var quote = quoteRepository.findCurrent(requestId).orElseThrow();
        var stale = new com.mediflow.billing.application.dto.command.ScheduledPaymentCommand(event.payload().reservationId(), 1,
                quote.patientPayable(), "VND", com.mediflow.billing.domain.model.PaymentMethod.CASH, null, UUID.randomUUID(), "PATIENT_PAYMENT");
        assertThatThrownBy(() -> payments.pay(requestId, stale, UUID.randomUUID(), "stale-quote", "quote-trace", CHANGED_AT.plusSeconds(60)))
                .isInstanceOf(BillingRuleException.class).hasMessageContaining("BILLING_QUOTE_STALE");
        var expired = new com.mediflow.billing.application.dto.command.ScheduledPaymentCommand(event.payload().reservationId(), 1,
                quote.patientPayable(), "VND", com.mediflow.billing.domain.model.PaymentMethod.CASH, null, quote.quoteId(), "PATIENT_PAYMENT");
        assertThatThrownBy(() -> payments.pay(requestId, expired, UUID.randomUUID(), "expired-quote", "quote-trace", quote.expiresAt()))
                .isInstanceOf(BillingRuleException.class).hasMessageContaining("BILLING_QUOTE_STALE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM scheduled_payment_operation WHERE payment_request_id=?", Integer.class, requestId)).isZero();
        verifyNoInteractions(clinical);
    }

    private void assertExecutable(UUID clearanceId, OutpatientRegistrationChangedEvent event) {
        UUID registrationId = event.payload().registrationId();
        var authority = clearanceAuthority.find(clearanceId).orElseThrow();
        assertThat(quoteRepository.findCurrent(jdbc.queryForObject(
                "SELECT payment_request_id FROM outpatient_registration_source WHERE registration_id=?",
                UUID.class, registrationId)).orElseThrow().expiresAt()).isBefore(Instant.now());
        assertThat(authority.expiresAt()).as("A paid registration must outlive the booking/quote TTL").isNull();
        assertThat(authority.eligible()).as("Current authority: %s", authority).isTrue();
        assertThat(authority.sourceId()).isEqualTo(registrationId);
        assertThat(authority.matchesSource("EXAM", registrationId)).isTrue();
        var command = new com.mediflow.billing.application.dto.command.StartOutpatientExecutionCommand(
                registrationId, "EXAM", registrationId, event.payload().patientId(),
                "OUTPATIENT_VISIT", registrationId);
        var started = executions.start(clearanceId, command, "scheduled-exam-start");
        assertThat(started.sourceId()).isEqualTo(registrationId);
        assertThat(executions.start(clearanceId, command, "scheduled-exam-replay")).isEqualTo(started);
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
