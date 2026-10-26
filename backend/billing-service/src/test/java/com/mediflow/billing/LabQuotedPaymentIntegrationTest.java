package com.mediflow.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.dto.command.LabTestChargeCommand;
import com.mediflow.billing.application.port.in.IssueLabTestChargeUseCase;
import com.mediflow.billing.application.port.out.FinancialClearanceAuthorityPort;
import com.mediflow.billing.application.port.out.OutpatientQuoteRepositoryPort;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "mediflow.jwt.secret=billing-lab-quoted-payment-secret-at-least-32-bytes",
        "mediflow.billing.ledger.enabled=true",
        "mediflow.billing.lab-test-charge-consumer.enabled=true",
        "mediflow.billing.outpatient-quotes.enabled=true",
        "billing.care-finance.prices.LAB-CBC.description=Complete blood count",
        "billing.care-finance.prices.LAB-CBC.unit-amount=100000",
        "billing.outpatient-quotes.catalogue-version=CAT-TEST-1",
        "billing.outpatient-quotes.policy-version=POLICY-TEST-1",
        "billing.outpatient-quotes.items[0].source-type=LAB_TEST",
        "billing.outpatient-quotes.items[0].price-code=LAB-CBC",
        "billing.outpatient-quotes.items[0].support-rate=0.5",
        "mediflow.billing.outbox.enabled=false",
        "mediflow.billing.outbox.metrics-enabled=false",
        "mediflow.billing.outbox.maintenance-enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class LabQuotedPaymentIntegrationTest {
    private static final String SECRET = "billing-lab-quoted-payment-secret-at-least-32-bytes";
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final RabbitMQContainer MQ = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("spring.rabbitmq.host", MQ::getHost);
        r.add("spring.rabbitmq.port", MQ::getAmqpPort);
        r.add("spring.rabbitmq.username", MQ::getAdminUsername);
        r.add("spring.rabbitmq.password", MQ::getAdminPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired IssueLabTestChargeUseCase issuances;
    @Autowired OutpatientQuoteRepositoryPort quotes;
    @Autowired FinancialClearanceAuthorityPort clearanceAuthority;
    @Autowired TestRestTemplate http;

    @BeforeEach void clean() { jdbc.execute("TRUNCATE BILLING_ACCOUNT, BILLING_EVENT_OUTBOX, lab_test_charge_delivery CASCADE"); }

    @Test void positiveCurrentLabQuoteIsPaidOnceThroughV2AndV1CannotBypassQuoteBinding() throws Exception {
        var command = labCommand();
        UUID requestId = issuances.issue(command);
        var quote = quotes.findCurrent(requestId).orElseThrow();
        assertThat(quote.grossAmount()).isEqualByComparingTo("100000");
        assertThat(quote.supportAmount()).isEqualByComparingTo("50000");
        assertThat(quote.patientPayable()).isEqualByComparingTo("50000");

        UUID actor = UUID.randomUUID();
        String path = "/api/v2/billing/payment-requests/" + requestId + "/lab-payments";
        var body = Map.of("quoteId", quote.quoteId(), "amount", "50000", "currency", "VND", "paymentMethod", "BANK_TRANSFER");
        var legacy = Map.of("idempotencyKey", "lab-v1-bypass", "amount", new BigDecimal("50000"),
                "currency", "VND", "paymentMethod", "CASH", "providerReference", "");
        var bypass = http.postForEntity("/api/v1/billing/payment-requests/" + requestId + "/payments",
                new HttpEntity<>(legacy, headers(actor, "CASHIER", "legacy-bypass")), String.class);
        assertThat(bypass.getStatusCode()).as(bypass.getBody()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PAYMENT_TRANSACTION WHERE payment_request_id=?", Integer.class, requestId)).isZero();

        var partial = Map.of("quoteId", quote.quoteId(), "amount", "49999", "currency", "VND", "paymentMethod", "CASH");
        var partialResponse = http.postForEntity(path,
                new HttpEntity<>(partial, headers(actor, "CASHIER", "lab-partial")), String.class);
        assertThat(partialResponse.getStatusCode()).as(partialResponse.getBody()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PAYMENT_TRANSACTION WHERE payment_request_id=?", Integer.class, requestId)).isZero();

        var headers = headers(actor, "CASHIER", "lab-positive-1");
        var first = http.postForEntity(path, new HttpEntity<>(body, headers), String.class);
        assertThat(first.getStatusCode()).as(first.getBody()).isEqualTo(HttpStatus.OK);
        var receipt = mapper.readTree(first.getBody()).path("data");
        assertThat(receipt.path("paymentRequestId").asText()).isEqualTo(requestId.toString());
        assertThat(receipt.path("amount").decimalValue()).isEqualByComparingTo("50000");
        assertThat(receipt.path("paymentMethod").asText()).isEqualTo("TRANSFER");
        UUID transactionId = UUID.fromString(receipt.path("transactionId").asText());

        var replay = http.postForEntity(path, new HttpEntity<>(body, headers(actor, "CASHIER", "lab-positive-1")), String.class);
        assertThat(replay.getStatusCode()).as(replay.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(replay.getBody()).path("data").path("transactionId").asText()).isEqualTo(transactionId.toString());

        var changedActor = http.postForEntity(path, new HttpEntity<>(body, headers(UUID.randomUUID(), "CASHIER", "lab-positive-1")), String.class);
        assertThat(changedActor.getStatusCode()).as(changedActor.getBody()).isEqualTo(HttpStatus.CONFLICT);
        var changedQuote = Map.of("quoteId", UUID.randomUUID(), "amount", "50000", "currency", "VND", "paymentMethod", "BANK_TRANSFER");
        var changedBinding = http.postForEntity(path,
                new HttpEntity<>(changedQuote, headers(actor, "CASHIER", "lab-positive-1")), String.class);
        assertThat(changedBinding.getStatusCode()).as(changedBinding.getBody()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PAYMENT_TRANSACTION WHERE payment_request_id=?", Integer.class, requestId)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PAYMENT_ALLOCATION WHERE transaction_id=?", Integer.class, transactionId)).isOne();
        assertThat(jdbc.queryForObject("SELECT status FROM PAYMENT_REQUEST WHERE payment_request_id=?", String.class, requestId)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM FINANCIAL_CLEARANCE WHERE payment_request_id=?", Integer.class, requestId)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lab_quoted_payment_receipt WHERE payment_request_id=?", Integer.class, requestId)).isOne();
        UUID clearanceId = jdbc.queryForObject("SELECT clearance_id FROM FINANCIAL_CLEARANCE WHERE payment_request_id=?", UUID.class, requestId);
        assertThat(clearanceAuthority.find(clearanceId).orElseThrow().ledgerEligible()).isTrue();

    }

    private static LabTestChargeCommand labCommand() {
        UUID lab = UUID.randomUUID();
        return new LabTestChargeCommand(UUID.randomUUID(), "a".repeat(64), "b".repeat(64), "lab-positive-source",
                lab, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "OUTPATIENT_VISIT", UUID.randomUUID(),
                UUID.randomUUID(), "LAB-CBC", "CBC", Instant.now().minusSeconds(1), null);
    }

    private static HttpHeaders headers(UUID actor, String role, String key) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(Jwts.builder().subject(actor.toString()).claim("role", role).claim("type", "access")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact());
        headers.set("Idempotency-Key", key);
        headers.set("X-Correlation-Id", "lab-positive-trace");
        return headers;
    }
}
