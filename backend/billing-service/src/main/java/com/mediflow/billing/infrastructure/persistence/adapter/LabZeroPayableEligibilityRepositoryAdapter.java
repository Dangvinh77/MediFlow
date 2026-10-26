package com.mediflow.billing.infrastructure.persistence.adapter;

import com.mediflow.billing.application.port.out.LabZeroPayableEligibilityRepositoryPort;
import com.mediflow.billing.application.port.out.LedgerPaymentRepositoryPort.PaymentContext;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.LabZeroPayableEligibilityReceipt;
import com.mediflow.billing.domain.model.OutpatientQuote;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Stores an atomic, local Lab zero-liability result and serializes against Lab cancellation. */
@Repository
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.outpatient-quotes.enabled",
        "mediflow.billing.zero-payable-eligibility.enabled"}, havingValue = "true")
@Transactional(propagation = Propagation.MANDATORY, timeout = 5)
public class LabZeroPayableEligibilityRepositoryAdapter implements LabZeroPayableEligibilityRepositoryPort {
    private final JdbcTemplate jdbc;

    public LabZeroPayableEligibilityRepositoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void lockIdempotencyKey(String key) {
        jdbc.execute("SET LOCAL lock_timeout='3s'");
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "lab-zero-eligibility:" + key);
    }

    @Override public void lockLabSource(UUID paymentRequestId) {
        var labIds = jdbc.queryForList("SELECT lab_id FROM lab_test_charge_source WHERE payment_request_id=?",
                UUID.class, paymentRequestId);
        if (labIds.size() != 1) fail("BILLING_LAB_TEST_SOURCE_CONFLICT");
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "lab-test-charge:" + labIds.getFirst());
    }

    @Override public Optional<LabZeroPayableEligibilityReceipt> findByIdempotencyKey(String key) {
        return jdbc.query("SELECT * FROM lab_zero_payable_eligibility WHERE idempotency_key=?",
                LabZeroPayableEligibilityRepositoryAdapter::receipt, key).stream().findFirst();
    }

    @Override public Optional<LabZeroPayableEligibilityReceipt> findByPaymentRequestId(UUID paymentRequestId) {
        return jdbc.query("SELECT * FROM lab_zero_payable_eligibility WHERE payment_request_id=?",
                LabZeroPayableEligibilityRepositoryAdapter::receipt, paymentRequestId).stream().findFirst();
    }

    @Override public void verifyExactLabBinding(UUID paymentRequestId, PaymentContext context, OutpatientQuote quote) {
        if (quote.lines().size() != 1) fail("BILLING_LAB_TEST_SOURCE_CONFLICT");
        var line = quote.lines().getFirst();
        var rows = jdbc.queryForList("""
                SELECT s.lab_id,s.source_order_id,s.patient_id AS source_patient,s.department_id AS source_department,
                       s.record_id,s.care_episode_type AS source_episode_type,s.care_episode_id AS source_episode_id,
                       s.price_code AS source_price_code,s.lab_type,r.purpose,r.status,r.requested_amount,r.expires_at,
                       r.account_id,a.patient_id,a.department_id,a.care_episode_type,a.care_episode_id,a.currency,
                       t.record_id AS target_record,t.lab_test_ids,c.source_type,c.source_id,c.price_code,c.quantity,
                       c.unit_amount,c.gross_amount,c.patient_id AS charge_patient,c.department_id AS charge_department,
                       rc.requested_amount AS linked_amount
                  FROM lab_test_charge_source s
                  JOIN PAYMENT_REQUEST r ON r.payment_request_id=s.payment_request_id
                  JOIN BILLING_ACCOUNT a ON a.account_id=r.account_id
                  JOIN PAYMENT_REQUEST_TARGET t ON t.payment_request_id=r.payment_request_id
                  JOIN PAYMENT_REQUEST_CHARGE rc ON rc.payment_request_id=r.payment_request_id
                  JOIN CHARGE c ON c.charge_id=rc.charge_id
                  JOIN outpatient_current_quote cq ON cq.payment_request_id=r.payment_request_id
                  JOIN outpatient_quote q ON q.payment_request_id=cq.payment_request_id AND q.quote_id=cq.quote_id
                 WHERE s.payment_request_id=? AND q.quote_id=? AND q.patient_payable=0 AND q.expires_at=r.expires_at
                 FOR UPDATE OF s
                """, paymentRequestId, quote.quoteId());
        if (rows.size() != 1) fail("BILLING_LAB_TEST_SOURCE_CONFLICT");
        var row = rows.getFirst();
        UUID labId = (UUID) row.get("lab_id");
        UUID[] rawLabIds = labIds(row.get("lab_test_ids"));
        var contextTarget = context.target();
        boolean exact = labId.equals(line.sourceId()) && "LAB_TEST".equals(line.sourceType())
                && line.priceCode().equals(row.get("source_price_code"))
                && line.quantity().compareTo(BigDecimal.ONE) == 0
                && paymentRequestId.equals(context.request().getPaymentRequestId())
                && context.account().getAccountId().equals(row.get("account_id"))
                && context.account().getPatientId().equals(row.get("patient_id"))
                && context.account().getDepartmentId().equals(row.get("department_id"))
                && "OUTPATIENT_VISIT".equals(row.get("source_episode_type"))
                && "OUTPATIENT_VISIT".equals(row.get("care_episode_type"))
                && context.account().getCareEpisodeId().equals(row.get("source_episode_id"))
                && context.account().getCareEpisodeId().equals(row.get("care_episode_id"))
                && context.account().getPatientId().equals(row.get("source_patient"))
                && context.account().getDepartmentId().equals(row.get("source_department"))
                && row.get("source_order_id") != null && row.get("lab_type") != null
                && labId.equals(row.get("source_id")) && "LAB_TEST".equals(row.get("source_type"))
                && line.priceCode().equals(row.get("price_code"))
                && context.request().getRequestedAmount().compareTo(line.patientPayable()) == 0
                && ((BigDecimal) row.get("requested_amount")).compareTo(BigDecimal.ZERO) == 0
                && ((BigDecimal) row.get("linked_amount")).compareTo(BigDecimal.ZERO) == 0
                && line.unitPrice().compareTo((BigDecimal) row.get("unit_amount")) == 0
                && line.grossAmount().compareTo((BigDecimal) row.get("gross_amount")) == 0
                && context.account().getPatientId().equals(row.get("charge_patient"))
                && context.account().getDepartmentId().equals(row.get("charge_department"))
                && line.patientPayable().compareTo((BigDecimal) row.get("linked_amount")) == 0
                && contextTarget.recordId() != null && contextTarget.recordId().equals(row.get("record_id"))
                && contextTarget.recordId().equals(row.get("target_record"))
                && Arrays.equals(rawLabIds, new UUID[]{labId})
                && contextTarget.labTestIds().equals(List.of(labId))
                && context.request().getPurpose().name().equals(row.get("purpose"))
                && "PENDING".equals(row.get("status"))
                && "VND".equals(row.get("currency").toString().trim());
        if (!exact) fail("BILLING_LAB_TEST_SOURCE_CONFLICT");
        Integer transactions = jdbc.queryForObject("SELECT count(*) FROM PAYMENT_TRANSACTION WHERE payment_request_id=?",
                Integer.class, paymentRequestId);
        Integer clearances = jdbc.queryForObject("SELECT count(*) FROM FINANCIAL_CLEARANCE WHERE payment_request_id=?",
                Integer.class, paymentRequestId);
        Integer operations = jdbc.queryForObject("SELECT count(*) FROM scheduled_payment_operation WHERE payment_request_id=?",
                Integer.class, paymentRequestId);
        if (transactions == null || clearances == null || operations == null || transactions != 0 || clearances != 0 || operations != 0)
            fail("BILLING_ZERO_ELIGIBILITY_CONFLICT");
    }

    @Override public void append(LabZeroPayableEligibilityReceipt receipt) {
        jdbc.update("""
                INSERT INTO lab_zero_payable_eligibility(eligibility_operation_id,payment_request_id,quote_id,
                    idempotency_key,actor_account_id,correlation_id,clearance_id,completed_at)
                VALUES (?,?,?,?,?,?,?,?)
                """, receipt.eligibilityOperationId(), receipt.paymentRequestId(), receipt.quoteId(),
                receipt.idempotencyKey(), receipt.actorAccountId(), receipt.correlationId(), receipt.clearanceId(),
                Timestamp.from(receipt.completedAt()));
    }

    private static LabZeroPayableEligibilityReceipt receipt(ResultSet rs, int row) throws SQLException {
        return new LabZeroPayableEligibilityReceipt(rs.getObject("eligibility_operation_id", UUID.class),
                rs.getObject("payment_request_id", UUID.class), rs.getObject("quote_id", UUID.class),
                rs.getString("idempotency_key"), rs.getObject("actor_account_id", UUID.class),
                rs.getString("correlation_id"), rs.getObject("clearance_id", UUID.class),
                rs.getTimestamp("completed_at").toInstant());
    }

    private static UUID[] labIds(Object value) {
        if (value instanceof UUID[] ids) return ids;
        if (!(value instanceof java.sql.Array)) fail("BILLING_LAB_TEST_SOURCE_CONFLICT");
        var array = (java.sql.Array) value;
        try {
            return (UUID[]) array.getArray();
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not read persisted Lab target IDs", failure);
        }
    }

    private static void fail(String code) { throw new BillingRuleException(code, code); }
}
