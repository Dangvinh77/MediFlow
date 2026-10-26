package com.mediflow.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.dto.command.LabTestChargeCommand;
import com.mediflow.billing.application.port.in.GrantLabZeroPayableEligibilityUseCase;
import com.mediflow.billing.application.port.in.IssueLabTestChargeUseCase;
import com.mediflow.billing.application.port.out.FinancialClearanceAuthorityPort;
import com.mediflow.billing.application.port.out.OutpatientQuoteRepositoryPort;
import com.mediflow.billing.domain.model.LabZeroPayableEligibilityReceipt;
import com.mediflow.common.exception.DuplicateResourceException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "mediflow.jwt.secret=billing-lab-zero-integration-secret-at-least-32-bytes",
        "mediflow.billing.ledger.enabled=true",
        "mediflow.billing.lab-test-charge-consumer.enabled=true",
        "mediflow.billing.outpatient-quotes.enabled=true",
        "mediflow.billing.zero-payable-eligibility.enabled=true",
        "billing.care-finance.prices.LAB-CBC.description=Complete blood count",
        "billing.care-finance.prices.LAB-CBC.unit-amount=100000",
        "billing.outpatient-quotes.catalogue-version=CAT-TEST-1",
        "billing.outpatient-quotes.policy-version=POLICY-TEST-1",
        "billing.outpatient-quotes.items[0].source-type=LAB_TEST",
        "billing.outpatient-quotes.items[0].price-code=LAB-CBC",
        "billing.outpatient-quotes.items[0].support-rate=1",
        "mediflow.billing.outbox.enabled=false",
        "mediflow.billing.outbox.metrics-enabled=false",
        "mediflow.billing.outbox.maintenance-enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class LabQuoteZeroLiabilityIntegrationTest {
    private static final String SECRET = "billing-lab-zero-integration-secret-at-least-32-bytes";
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
    @Autowired GrantLabZeroPayableEligibilityUseCase eligibility;
    @Autowired FinancialClearanceAuthorityPort clearanceAuthority;
    @Autowired OutpatientQuoteRepositoryPort quotes;

    @BeforeEach void resetLedger() {
        jdbc.execute("TRUNCATE BILLING_ACCOUNT, BILLING_EVENT_OUTBOX, lab_test_charge_delivery CASCADE");
    }

    @Test void zeroQuoteProducesOneImmutableClearanceReceiptAndHeldOutboxWithoutCashTransaction() throws Exception {
        var command = command();
        UUID paymentRequestId = issuances.issue(command);
        var quote = quotes.findCurrent(paymentRequestId).orElseThrow();
        assertThat(quote.patientPayable()).isZero();
        assertThat(quote.grossAmount()).isEqualByComparingTo("100000");
        assertThat(jdbc.queryForObject("SELECT requested_amount FROM PAYMENT_REQUEST WHERE payment_request_id=?",
                java.math.BigDecimal.class, paymentRequestId)).isZero();
        assertThat(jdbc.queryForObject("SELECT gross_amount FROM CHARGE WHERE source_id=?",
                java.math.BigDecimal.class, command.labId())).isEqualByComparingTo("100000");

        UUID actor = UUID.randomUUID();
        LabZeroPayableEligibilityReceipt first = eligibility.grant(paymentRequestId, quote.quoteId(), actor,
                "lab-zero-cbc-1", "corr-zero-1");
        LabZeroPayableEligibilityReceipt replay = eligibility.grant(paymentRequestId, quote.quoteId(), actor,
                "lab-zero-cbc-1", "corr-retry");

        assertThat(replay).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT status FROM PAYMENT_REQUEST WHERE payment_request_id=?", String.class,
                paymentRequestId)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PAYMENT_TRANSACTION WHERE payment_request_id=?",
                Integer.class, paymentRequestId)).isZero();
        var clearance = jdbc.queryForMap("SELECT amount,payment_method,purpose,record_id,lab_test_ids FROM FINANCIAL_CLEARANCE WHERE clearance_id=?",
                first.clearanceId());
        assertThat((java.math.BigDecimal) clearance.get("amount")).isZero();
        assertThat(clearance.get("payment_method")).isEqualTo("ZERO_LIABILITY");
        assertThat(clearance.get("purpose")).isEqualTo("LAB_TEST");
        assertThat(clearance.get("record_id")).isEqualTo(command.recordId());
        assertThat((UUID[]) ((java.sql.Array) clearance.get("lab_test_ids")).getArray()).containsExactly(command.labId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lab_zero_payable_eligibility WHERE payment_request_id=?",
                Integer.class, paymentRequestId)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM BILLING_EVENT_OUTBOX WHERE routing_key='financial.clearance.granted'",
                Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT publication_enabled FROM BILLING_EVENT_OUTBOX WHERE routing_key='financial.clearance.granted'",
                Boolean.class)).isFalse();
        assertThat(clearanceAuthority.find(first.clearanceId()).orElseThrow().ledgerEligible()).isTrue();
        var event = mapper.readTree(jdbc.queryForObject(
                "SELECT payload FROM BILLING_EVENT_OUTBOX WHERE routing_key='financial.clearance.granted'", String.class)).path("payload");
        assertThat(event.path("labTestIds").get(0).asText()).isEqualTo(command.labId().toString());
        assertThat(event.path("amount").decimalValue()).isZero();
        assertThat(event.path("paymentMethod").asText()).isEqualTo("ZERO_LIABILITY");

        assertThatThrownBy(() -> eligibility.grant(paymentRequestId, quote.quoteId(), UUID.randomUUID(),
                "lab-zero-cbc-1", "corr-other-actor"))
                .isInstanceOf(DuplicateResourceException.class).hasFieldOrPropertyWithValue("code", "BILLING_IDEMPOTENCY_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM FINANCIAL_CLEARANCE WHERE payment_request_id=?",
                Integer.class, paymentRequestId)).isOne();
        assertThatThrownBy(() -> jdbc.update("UPDATE lab_zero_payable_eligibility SET correlation_id='tampered' WHERE payment_request_id=?",
                paymentRequestId)).isInstanceOf(UncategorizedSQLException.class);
    }

    private static LabTestChargeCommand command() {
        UUID lab = UUID.randomUUID();
        return new LabTestChargeCommand(UUID.randomUUID(), "a".repeat(64), "b".repeat(64), "lab-zero-source",
                lab, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "OUTPATIENT_VISIT", UUID.randomUUID(),
                UUID.randomUUID(), "LAB-CBC", "CBC", Instant.now().minusSeconds(1), null);
    }
}
