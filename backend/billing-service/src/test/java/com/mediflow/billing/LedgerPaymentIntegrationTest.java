package com.mediflow.billing;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.dto.request.CompleteLedgerPaymentRequest;
import com.mediflow.billing.application.port.in.ProcessLedgerPaymentUseCase;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.PaymentMethod;
import com.mediflow.billing.infrastructure.persistence.repository.BillingEventOutboxJpaRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class LedgerPaymentIntegrationTest {
    private static final String SECRET = "billing-ledger-integration-secret-at-least-32-bytes";
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
        registry.add("mediflow.billing.outbox.enabled", () -> false);
        registry.add("mediflow.billing.outbox.metrics-enabled", () -> false);
        registry.add("mediflow.billing.outbox.maintenance-enabled", () -> false);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired ProcessLedgerPaymentUseCase payments;
    @Autowired PlatformTransactionManager manager;
    @Autowired BillingEventOutboxJpaRepository outbox;
    @Autowired ObjectMapper mapper;
    @Autowired TestRestTemplate http;
    private UUID account, patient, department, request, invoice, episode, source, actor;

    @BeforeEach void setup() {
        // These tables belong to the fresh Testcontainers database, never a configured developer DB.
        jdbc.execute("TRUNCATE BILLING_ACCOUNT, BILLING_EVENT_OUTBOX CASCADE");
        account = UUID.randomUUID(); patient = UUID.randomUUID(); department = UUID.randomUUID();
        request = UUID.randomUUID(); invoice = UUID.randomUUID(); episode = UUID.randomUUID();
        source = UUID.randomUUID(); actor = UUID.randomUUID();
    }

    @Test void threeInstallmentsHaveThreeReceiptsOneClearanceAndNoDoubleEffect() throws Exception {
        seed("PRESCRIPTION", "OUTPATIENT_VISIT", "100");
        var first = payments.complete(request, command("first", "20"), actor, "trace-1");
        payments.complete(request, command("second", "30"), actor, "trace-2");
        payments.complete(request, command("third", "50"), actor, "trace-3");
        assertThat(payments.complete(request, command("first", "20.00"), actor, "retry").transactionId()).isEqualTo(first.transactionId());
        assertThat(count("PAYMENT_TRANSACTION")).isEqualTo(3);
        assertThat(count("FINANCIAL_CLEARANCE")).isEqualTo(1);
        assertThat(count("BILLING_EVENT_OUTBOX")).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM PAYMENT_ALLOCATION", BigDecimal.class)).isEqualByComparingTo("100");
        String body = jdbc.queryForObject("SELECT payload FROM BILLING_EVENT_OUTBOX WHERE routing_key='financial.clearance.granted'", String.class);
        var event = mapper.readTree(body);
        assertThat(event.path("producer").asText()).isEqualTo("billing-service");
        assertThat(event.path("payload").path("prescriptionId").asText()).isEqualTo(source.toString());
        assertThat(event.path("payload").path("careEpisodeId").asText()).isEqualTo(episode.toString());
        assertThat(event.path("payload").path("amount").decimalValue()).isEqualByComparingTo("100");
        assertThat(event.path("payload").path("clearanceId").asText()).isNotEqualTo(request.toString());
    }

    @Test void depositProducesLiabilityReceiptAndNoEarnedAllocation() throws Exception {
        seed("ADMISSION_DEPOSIT", "ADMISSION", "100");
        payments.complete(request, command("deposit", "100"), actor, "deposit-trace");
        assertThat(count("PAYMENT_ALLOCATION")).isZero();
        var receipt = mapper.readTree(jdbc.queryForObject("SELECT payload FROM BILLING_EVENT_OUTBOX WHERE routing_key='payment.completed'", String.class));
        assertThat(receipt.path("payload").path("classification").asText()).isEqualTo("ADMISSION_DEPOSIT");
        var clearance = mapper.readTree(jdbc.queryForObject("SELECT payload FROM BILLING_EVENT_OUTBOX WHERE routing_key='financial.clearance.granted'", String.class));
        assertThat(clearance.path("payload").path("admissionId").asText()).isEqualTo(episode.toString());
    }

    @Test void surgeryClearanceBindsExactCaseAndAdmission() throws Exception {
        seed("SURGERY", "ADMISSION", "100");
        payments.complete(request, command("surgery", "100"), actor, "surgery-trace");
        var event = mapper.readTree(jdbc.queryForObject("SELECT payload FROM BILLING_EVENT_OUTBOX WHERE routing_key='financial.clearance.granted'", String.class));
        assertThat(event.path("payload").path("surgeryCaseId").asText()).isEqualTo(source.toString());
        assertThat(event.path("payload").path("admissionId").asText()).isEqualTo(episode.toString());
    }

    @Test void wrongTargetOrCrossAccountChargeFailsClosedWithoutReceipt() {
        seed("PRESCRIPTION", "OUTPATIENT_VISIT", "100");
        jdbc.update("UPDATE PAYMENT_REQUEST_TARGET SET prescription_id=?", UUID.randomUUID());
        assertThatThrownBy(() -> payments.complete(request, command("wrong", "100"), actor, "trace"))
                .isInstanceOf(BillingRuleException.class).hasMessage("BILLING_REQUEST_SOURCE_TARGET_MISMATCH");
        assertThat(count("PAYMENT_TRANSACTION")).isZero();
        assertThat(count("BILLING_EVENT_OUTBOX")).isZero();
    }

    @Test void racingInstallmentsCannotOverpayOneRequest() throws Exception {
        seed("PRESCRIPTION", "OUTPATIENT_VISIT", "100");
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> attempt(start, "race-a", "80"));
            var b = workers.submit(() -> attempt(start, "race-b", "80"));
            start.countDown();
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("COMPLETED", "BILLING_PAYMENT_EXCEEDS_REQUEST");
        }
        assertThat(count("PAYMENT_TRANSACTION")).isEqualTo(1);
        assertThat(count("FINANCIAL_CLEARANCE")).isZero();
    }

    @Test void racingSameKeyReturnsOneImmutableTransaction() throws Exception {
        seed("PRESCRIPTION", "OUTPATIENT_VISIT", "100");
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> { start.await(); return payments.complete(request, command("same", "100"), actor, "a"); });
            var b = workers.submit(() -> { start.await(); return payments.complete(request, command("same", "100"), actor, "b"); });
            start.countDown();
            assertThat(a.get(15, TimeUnit.SECONDS).transactionId()).isEqualTo(b.get(15, TimeUnit.SECONDS).transactionId());
        }
        assertThat(count("PAYMENT_TRANSACTION")).isEqualTo(1);
        assertThat(count("FINANCIAL_CLEARANCE")).isEqualTo(1);
        assertThat(count("BILLING_EVENT_OUTBOX")).isEqualTo(2);
    }

    @Test void outboxInsertFailureRollsBackPaymentAllocationAndClearance() {
        seed("PRESCRIPTION", "OUTPATIENT_VISIT", "100");
        jdbc.execute("CREATE FUNCTION reject_ledger_event() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test rollback'; END $$");
        jdbc.execute("CREATE TRIGGER reject_ledger_event BEFORE INSERT ON BILLING_EVENT_OUTBOX FOR EACH ROW EXECUTE FUNCTION reject_ledger_event()");
        try {
            assertThatThrownBy(() -> payments.complete(request, command("rollback", "100"), actor, "trace")).isInstanceOf(RuntimeException.class);
            assertThat(count("PAYMENT_TRANSACTION")).isZero();
            assertThat(count("PAYMENT_ALLOCATION")).isZero();
            assertThat(count("FINANCIAL_CLEARANCE")).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM PAYMENT_REQUEST", String.class)).isEqualTo("PENDING");
        } finally {
            jdbc.execute("DROP TRIGGER reject_ledger_event ON BILLING_EVENT_OUTBOX");
            jdbc.execute("DROP FUNCTION reject_ledger_event()");
        }
    }

    @Test void legacyDispatcherDoesNotClaimHeldV1EventsEvenAfterReplay() {
        seed("PRESCRIPTION", "OUTPATIENT_VISIT", "100");
        payments.complete(request, command("held", "100"), actor, "trace");
        assertThatThrownBy(() -> jdbc.update("UPDATE BILLING_EVENT_OUTBOX SET publication_enabled=TRUE WHERE contract_version=1"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        var transactions = new TransactionTemplate(manager);
        var held = transactions.execute(status -> outbox.findClaimable(Instant.now().plusSeconds(10), Instant.now().minusSeconds(60), 10));
        assertThat(held).isEmpty();
        UUID eventId = jdbc.queryForObject("SELECT event_id FROM BILLING_EVENT_OUTBOX LIMIT 1", UUID.class);
        jdbc.update("UPDATE BILLING_EVENT_OUTBOX SET quarantined_at=now() WHERE event_id=?", eventId);
        transactions.executeWithoutResult(status -> outbox.replay(eventId, Instant.now()));
        var replayed = transactions.execute(status -> outbox.findClaimable(Instant.now().plusSeconds(10), Instant.now().minusSeconds(60), 10));
        assertThat(replayed).isEmpty();
        UUID legacyId = UUID.randomUUID();
        jdbc.update("INSERT INTO BILLING_EVENT_OUTBOX(event_id,routing_key,aggregate_id,payload) VALUES (?,?,?,?)",
                legacyId, "invoice.created", UUID.randomUUID(), "{}");
        var legacy = transactions.execute(status -> outbox.findClaimable(Instant.now().plusSeconds(10), Instant.now().minusSeconds(60), 10));
        assertThat(legacy)
                .extracting(row -> row.getEventId()).containsExactly(legacyId);
    }

    @Test void httpRequiresCashierAndStoresVerifiedActorNotSpoofedHeader() {
        seed("PRESCRIPTION", "OUTPATIENT_VISIT", "100");
        String path = "/api/v1/billing/payment-requests/" + request + "/payments";
        assertThat(http.postForEntity(path, command("http", "100"), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var headers = headers("DOCTOR");
        assertThat(http.postForEntity(path, new HttpEntity<>(command("http", "100"), headers), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        headers = headers("CASHIER");
        headers.set("X-Actor-Account-Id", UUID.randomUUID().toString());
        headers.set("X-Correlation-Id", "http-trace");
        var response = http.postForEntity(path, new HttpEntity<>(command("http", "100"), headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT actor_account_id FROM PAYMENT_TRANSACTION", UUID.class)).isEqualTo(actor);
        assertThat(response.getBody()).contains("http-trace");
    }

    @Test void surgeryLookup_exactLedgerGrantIsActiveAndMissingGrantIsConfirmedAbsence() throws Exception {
        UUID clearance = paidSurgery();
        var response = lookup(clearance, serviceHeaders("service","SYSTEM","surgery-service",60));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isEqualTo("lookup-trace");
        var root = mapper.readTree(response.getBody());
        assertThat(root.path("correlationId").asText()).isEqualTo("lookup-trace");
        var data = root.path("data");
        assertThat(data.path("eligible").asBoolean()).isTrue();
        assertThat(data.path("patientId").asText()).isEqualTo(patient.toString());
        assertThat(data.path("surgeryCaseId").asText()).isEqualTo(source.toString());
        assertThat(data.path("admissionId").asText()).isEqualTo(episode.toString());
        assertThat(data.has("amount")).isFalse();
        var missing = mapper.readTree(lookup(UUID.randomUUID(),serviceHeaders("service","SYSTEM","surgery-service",60)).getBody()).path("data");
        assertThat(missing.path("exists").asBoolean()).isFalse();
        assertThat(missing.path("eligible").asBoolean()).isFalse();
        assertThat(missing.path("patientId").isNull()).isTrue();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"revoked","expired","refund","voided-charge","wrong-patient",
            "wrong-invoice","wrong-target","cancelled-request","closed-account","emergency-override","missing-target","wrong-currency"})
    void surgeryLookup_invalidCurrentLedgerNeverGrantsReadiness(String changed) throws Exception {
        UUID clearance = paidSurgery();
        switch(changed) {
            case "revoked" -> jdbc.update("UPDATE FINANCIAL_CLEARANCE SET revoked_at=now()");
            case "expired" -> jdbc.update("UPDATE FINANCIAL_CLEARANCE SET expires_at=granted_at");
            case "voided-charge" -> jdbc.update("UPDATE CHARGE SET status='VOIDED'");
            case "wrong-patient" -> jdbc.update("UPDATE FINANCIAL_CLEARANCE SET patient_id=?",UUID.randomUUID());
            case "wrong-invoice" -> jdbc.update("UPDATE FINANCIAL_CLEARANCE SET invoice_id=?",UUID.randomUUID());
            case "wrong-target" -> jdbc.update("UPDATE PAYMENT_REQUEST_TARGET SET surgery_case_id=?",UUID.randomUUID());
            case "cancelled-request" -> jdbc.update("UPDATE PAYMENT_REQUEST SET status='CANCELLED'");
            case "closed-account" -> jdbc.update("UPDATE BILLING_ACCOUNT SET status='CLOSED'");
            case "emergency-override" -> jdbc.update("UPDATE FINANCIAL_CLEARANCE SET emergency_override=TRUE");
            case "missing-target" -> jdbc.update("DELETE FROM PAYMENT_REQUEST_TARGET");
            case "wrong-currency" -> jdbc.update("UPDATE FINANCIAL_CLEARANCE SET currency='USD'");
            case "refund" -> jdbc.update("""
                    INSERT INTO PAYMENT_TRANSACTION(transaction_id,account_id,payment_request_id,transaction_type,
                        classification,status,amount,currency,payment_method,idempotency_key,original_transaction_id,completed_at)
                    SELECT ?,account_id,payment_request_id,'REFUND',classification,'COMPLETED',1,currency,payment_method,
                        'test-refund',transaction_id,now() FROM PAYMENT_TRANSACTION WHERE transaction_type='PAYMENT'
                    """, UUID.randomUUID());
            default -> throw new AssertionError(changed);
        }
        var result = lookup(clearance,serviceHeaders("service","SYSTEM","surgery-service",60));
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(result.getBody()).path("data").path("exists").asBoolean()).isTrue();
        assertThat(mapper.readTree(result.getBody()).path("data").path("eligible").asBoolean()).isFalse();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"access,SYSTEM,surgery-service,60", "refresh,SYSTEM,surgery-service,60",
            "service,ADMIN,surgery-service,60", "service,SYSTEM,unknown-service,60", "service,SYSTEM,surgery-service,120"})
    void surgeryLookup_rejectsHumanRefreshWrongRoleWrongSubjectAndLongLivedCredentials(String type,String role,String subject,int ttl) {
        assertThat(lookup(UUID.randomUUID(),serviceHeaders(type,role,subject,ttl)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void surgeryServiceCredentialCannotCallHumanPaymentApi() {
        seed("SURGERY","ADMISSION","100");
        var result = http.postForEntity("/api/v1/billing/payment-requests/"+request+"/payments",
                new HttpEntity<>(command("must-not-pay","100"),serviceHeaders("service","SYSTEM","surgery-service",60)),String.class);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(count("PAYMENT_TRANSACTION")).isZero();
    }

    private UUID paidSurgery() {
        seed("SURGERY","ADMISSION","100");
        payments.complete(request,command("lookup-paid","100"),actor,"lookup-trace");
        return jdbc.queryForObject("SELECT clearance_id FROM FINANCIAL_CLEARANCE",UUID.class);
    }
    private ResponseEntity<String> lookup(UUID clearance,HttpHeaders headers) {
        headers.set("X-Correlation-Id","lookup-trace");
        return http.exchange("/api/v1/billing/financial-clearances/"+clearance+"/lookup",HttpMethod.GET,new HttpEntity<>(headers),String.class);
    }
    private HttpHeaders serviceHeaders(String type,String role,String subject,int ttl) {
        var headers = new HttpHeaders();
        Instant issued = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        headers.setBearerAuth(Jwts.builder().subject(subject).claim("role",role).claim("type",type)
                .issuedAt(Date.from(issued)).expiration(Date.from(issued.plusSeconds(ttl)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact());
        return headers;
    }

    private String attempt(CountDownLatch start, String key, String amount) throws InterruptedException {
        start.await();
        try { payments.complete(request, command(key, amount), actor, "race"); return "COMPLETED"; }
        catch (BillingRuleException rejected) { return rejected.getCode(); }
    }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
    private CompleteLedgerPaymentRequest command(String key, String amount) {
        return new CompleteLedgerPaymentRequest(key, new BigDecimal(amount), "VND", PaymentMethod.CASH, null);
    }
    private HttpHeaders headers(String role) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(Jwts.builder().subject(actor.toString()).claim("role", role).claim("type", "access")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact());
        return headers;
    }
    private void seed(String purpose, String episodeType, String total) {
        jdbc.update("""
                INSERT INTO BILLING_ACCOUNT(account_id,patient_id,department_id,care_episode_type,care_episode_id,opened_at)
                VALUES (?,?,?,?,?,now())
                """, account, patient, department, episodeType, episode);
        jdbc.update("""
                INSERT INTO PAYMENT_REQUEST(payment_request_id,invoice_id,account_id,purpose,requested_amount,created_by)
                VALUES (?,?,?,?,?,?)
                """, request, invoice, account, purpose, new BigDecimal(total), actor);
        boolean deposit = "ADMISSION_DEPOSIT".equals(purpose), surgery = "SURGERY".equals(purpose);
        jdbc.update("""
                INSERT INTO PAYMENT_REQUEST_TARGET(payment_request_id,prescription_id,admission_id,surgery_case_id)
                VALUES (?,?,?,?)
                """, request, "PRESCRIPTION".equals(purpose) ? source : null,
                deposit || surgery && "ADMISSION".equals(episodeType) ? episode : null, surgery ? source : null);
        if (!deposit) {
            UUID charge = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO CHARGE(charge_id,account_id,patient_id,department_id,source_type,source_id,price_code,
                        description,unit_amount,gross_amount,incurred_at) VALUES (?,?,?,?,?,?,?,?,?,?,now())
                    """, charge, account, patient, department, purpose, source, "TEST_PRICE", "Test-only price",
                    new BigDecimal(total), new BigDecimal(total));
            jdbc.update("INSERT INTO PAYMENT_REQUEST_CHARGE(payment_request_id,charge_id,requested_amount) VALUES (?,?,?)",
                    request, charge, new BigDecimal(total));
        }
    }
}
