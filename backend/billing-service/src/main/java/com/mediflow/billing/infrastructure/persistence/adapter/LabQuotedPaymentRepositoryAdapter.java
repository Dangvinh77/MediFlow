package com.mediflow.billing.infrastructure.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.port.out.LabQuotedPaymentRepositoryPort;
import com.mediflow.billing.application.port.out.LedgerPaymentRepositoryPort.PaymentContext;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.LabQuotedPaymentReceipt;
import com.mediflow.billing.domain.model.OutpatientQuote;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Serializes a Lab source with cancellation and stores immutable quote-bound payment evidence. */
@Repository
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.outpatient-quotes.enabled"}, havingValue = "true")
@Transactional(propagation = Propagation.MANDATORY, timeout = 5)
public class LabQuotedPaymentRepositoryAdapter implements LabQuotedPaymentRepositoryPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public LabQuotedPaymentRepositoryAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override public boolean isQuoteBoundLabRequest(UUID paymentRequestId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM PAYMENT_REQUEST r
                  JOIN outpatient_current_quote q ON q.payment_request_id=r.payment_request_id
                 WHERE r.payment_request_id=? AND r.purpose='LAB_TEST')
                """, Boolean.class, paymentRequestId));
    }

    @Override public void lockLabSource(UUID paymentRequestId) {
        jdbc.execute("SET LOCAL lock_timeout='3s'");
        List<UUID> labIds = jdbc.queryForList("SELECT lab_id FROM lab_test_charge_source WHERE payment_request_id=?",
                UUID.class, paymentRequestId);
        if (labIds.size() != 1) fail("BILLING_LAB_TEST_SOURCE_CONFLICT");
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "lab-test-charge:" + labIds.getFirst());
    }

    @Override public OutpatientQuote requireCurrentPayableQuote(UUID paymentRequestId, PaymentContext context,
            UUID quoteId, BigDecimal amount, Instant now) {
        var rows = jdbc.query("""
                SELECT s.lab_id,s.source_order_id,s.patient_id AS source_patient,s.department_id AS source_department,
                       s.record_id,s.care_episode_type AS source_episode_type,s.care_episode_id AS source_episode_id,
                       s.price_code AS source_price_code,s.lab_type,
                       r.purpose,r.status,r.requested_amount,r.currency AS request_currency,r.expires_at,
                       a.account_id,a.patient_id,a.department_id,a.care_episode_type,a.care_episode_id,a.currency AS account_currency,
                       t.record_id AS target_record,t.lab_test_ids,
                       rc.requested_amount AS linked_amount,c.source_type,c.source_id,c.price_code,c.description,c.status AS charge_status,c.quantity,
                       c.unit_amount,c.gross_amount,c.patient_id AS charge_patient,c.department_id AS charge_department,
                       q.quote_id,q.patient_id AS quote_patient,q.care_episode_id AS quote_episode_id,
                       q.patient_payable,q.expires_at AS quote_expires_at,q.snapshot,cq.quote_id AS current_quote_id
                  FROM PAYMENT_REQUEST r
                  JOIN BILLING_ACCOUNT a ON a.account_id=r.account_id
                  JOIN PAYMENT_REQUEST_TARGET t ON t.payment_request_id=r.payment_request_id
                  JOIN lab_test_charge_source s ON s.payment_request_id=r.payment_request_id
                  JOIN PAYMENT_REQUEST_CHARGE rc ON rc.payment_request_id=r.payment_request_id
                  JOIN CHARGE c ON c.charge_id=rc.charge_id
                  JOIN outpatient_current_quote cq ON cq.payment_request_id=r.payment_request_id
                  JOIN outpatient_quote q ON q.payment_request_id=cq.payment_request_id AND q.quote_id=cq.quote_id
                 WHERE r.payment_request_id=? AND q.quote_id=?
                 FOR UPDATE OF s
                """, (rs, row) -> new BindingRow(rs.getObject("lab_id", UUID.class),
                rs.getObject("source_order_id", UUID.class), rs.getObject("source_patient", UUID.class),
                rs.getObject("source_department", UUID.class), rs.getObject("record_id", UUID.class),
                rs.getString("source_episode_type"), rs.getObject("source_episode_id", UUID.class),
                rs.getString("source_price_code"), rs.getString("lab_type"), rs.getString("purpose"),
                rs.getString("status"), rs.getBigDecimal("requested_amount"), rs.getString("request_currency").trim(),
                rs.getObject("account_id", UUID.class), rs.getObject("patient_id", UUID.class),
                rs.getObject("department_id", UUID.class), rs.getString("care_episode_type"),
                rs.getObject("care_episode_id", UUID.class), rs.getString("account_currency").trim(),
                rs.getObject("target_record", UUID.class), labIds(rs), rs.getBigDecimal("linked_amount"),
                rs.getString("source_type"), rs.getObject("source_id", UUID.class), rs.getString("price_code"),
                rs.getString("description"), rs.getString("charge_status"), rs.getBigDecimal("quantity"),
                rs.getBigDecimal("unit_amount"), rs.getBigDecimal("gross_amount"),
                rs.getObject("charge_patient", UUID.class), rs.getObject("charge_department", UUID.class),
                rs.getObject("quote_id", UUID.class), rs.getObject("quote_patient", UUID.class),
                rs.getObject("quote_episode_id", UUID.class), rs.getBigDecimal("patient_payable"),
                rs.getTimestamp("quote_expires_at").toInstant(), readQuote(rs.getString("snapshot")),
                rs.getObject("current_quote_id", UUID.class)), paymentRequestId, quoteId);
        if (rows.size() != 1) fail("BILLING_QUOTE_STALE");
        BindingRow row = rows.getFirst();
        var account = context.account();
        var request = context.request();
        var target = context.target();
        if (request.getPurpose() != com.mediflow.billing.domain.model.PaymentRequestPurpose.LAB_TEST
                || !"PENDING".equals(row.status()) || row.requestedAmount().signum() <= 0
                || context.completedAmount().signum() != 0) fail("BILLING_REQUEST_NOT_PAYABLE");
        if (request.getExpiresAt() == null || !now.isBefore(request.getExpiresAt())) fail("BILLING_REQUEST_EXPIRED");
        OutpatientQuote quote = row.quote();
        if (!quote.quoteId().equals(quoteId) || !quote.isPayableAt(now)
                || !quote.quoteId().equals(row.currentQuoteId())) fail("BILLING_QUOTE_STALE");
        if (quote.patientPayable().compareTo(amount) != 0 || amount.compareTo(request.getRequestedAmount()) != 0
                || quote.patientPayable().compareTo(row.patientPayable()) != 0
                || row.linkedAmount().compareTo(amount) != 0) fail("BILLING_QUOTE_AMOUNT_CONFLICT");
        if (quote.lines().size() != 1) fail("BILLING_LAB_TEST_SOURCE_CONFLICT");
        var line = quote.lines().getFirst();
        boolean exact = account.getStatus() == com.mediflow.billing.domain.model.AccountStatus.OPEN
                && account.getCareEpisodeType() == com.mediflow.billing.domain.model.CareEpisodeType.OUTPATIENT_VISIT
                && account.getAccountId().equals(row.accountId()) && account.getPatientId().equals(row.patientId())
                && account.getDepartmentId().equals(row.departmentId()) && account.getCurrency().equals(row.accountCurrency())
                && account.getCareEpisodeId().equals(row.careEpisodeId())
                && request.getPaymentRequestId().equals(paymentRequestId)
                && request.getRequestedAmount().compareTo(row.requestedAmount()) == 0
                && "LAB_TEST".equals(row.purpose()) && "VND".equals(row.requestCurrency())
                && row.sourceOrderId() != null && row.labType() != null
                && account.getPatientId().equals(row.sourcePatient()) && account.getDepartmentId().equals(row.sourceDepartment())
                && "OUTPATIENT_VISIT".equals(row.sourceEpisodeType())
                && account.getCareEpisodeId().equals(row.sourceEpisodeId())
                && row.labId().equals(row.sourceId()) && "LAB_TEST".equals(row.sourceType())
                && row.sourcePriceCode().equals(row.priceCode()) && row.sourcePriceCode().equals(line.priceCode())
                && "POSTED".equals(row.chargeStatus()) && line.description().equals(row.description())
                && row.labId().equals(line.sourceId()) && "LAB_TEST".equals(line.sourceType())
                && line.quantity().compareTo(BigDecimal.ONE) == 0
                && line.quantity().compareTo(row.quantity()) == 0
                && quote.patientId().equals(account.getPatientId()) && quote.careEpisodeId().equals(account.getCareEpisodeId())
                && "OUTPATIENT_VISIT".equals(quote.careEpisodeType())
                && row.quotePatient().equals(account.getPatientId()) && row.quoteEpisodeId().equals(account.getCareEpisodeId())
                && row.targetRecord() != null && row.targetRecord().equals(row.recordId())
                && Arrays.equals(row.labIds(), new UUID[]{row.labId()})
                && target != null && target.recordId() != null && target.recordId().equals(row.recordId())
                && target.labTestIds().equals(List.of(row.labId()))
                && target.appointmentId() == null && target.prescriptionId() == null
                && target.admissionId() == null && target.surgeryCaseId() == null
                && sameDatabaseInstant(quote.expiresAt(), request.getExpiresAt())
                && sameDatabaseInstant(quote.expiresAt(), row.quoteExpiresAt())
                && line.unitPrice().compareTo(row.unitAmount()) == 0
                && line.grossAmount().compareTo(row.grossAmount()) == 0
                && account.getPatientId().equals(row.chargePatient()) && account.getDepartmentId().equals(row.chargeDepartment());
        if (!exact) fail("BILLING_LAB_TEST_SOURCE_CONFLICT");
        Integer linked = jdbc.queryForObject("SELECT count(*) FROM PAYMENT_REQUEST_CHARGE WHERE payment_request_id=?", Integer.class, paymentRequestId);
        Integer transactions = jdbc.queryForObject("SELECT count(*) FROM PAYMENT_TRANSACTION WHERE payment_request_id=?", Integer.class, paymentRequestId);
        Integer clearances = jdbc.queryForObject("SELECT count(*) FROM FINANCIAL_CLEARANCE WHERE payment_request_id=?", Integer.class, paymentRequestId);
        Integer operations = jdbc.queryForObject("SELECT count(*) FROM scheduled_payment_operation WHERE payment_request_id=?", Integer.class, paymentRequestId);
        Integer zero = jdbc.queryForObject("SELECT count(*) FROM lab_zero_payable_eligibility WHERE payment_request_id=?", Integer.class, paymentRequestId);
        Integer previous = jdbc.queryForObject("SELECT count(*) FROM lab_quoted_payment_receipt WHERE payment_request_id=?", Integer.class, paymentRequestId);
        if (linked == null || transactions == null || clearances == null || operations == null || zero == null
                || previous == null || linked != 1 || transactions != 0 || clearances != 0 || operations != 0
                || zero != 0 || previous != 0) fail("BILLING_LAB_TEST_SOURCE_CONFLICT");
        return quote;
    }

    @Override public Optional<LabQuotedPaymentReceipt> findByIdempotencyKey(String key) {
        return jdbc.query("SELECT * FROM lab_quoted_payment_receipt WHERE idempotency_key=?",
                LabQuotedPaymentRepositoryAdapter::receipt, key).stream().findFirst();
    }

    @Override public Optional<LabQuotedPaymentReceipt> findByPaymentRequestId(UUID requestId) {
        return jdbc.query("SELECT * FROM lab_quoted_payment_receipt WHERE payment_request_id=?",
                LabQuotedPaymentRepositoryAdapter::receipt, requestId).stream().findFirst();
    }

    @Override public UUID requireClearanceId(UUID paymentRequestId) {
        var rows = jdbc.queryForList("SELECT clearance_id FROM FINANCIAL_CLEARANCE WHERE payment_request_id=?",
                UUID.class, paymentRequestId);
        if (rows.size() != 1) fail("BILLING_LAB_TEST_SOURCE_CONFLICT");
        return rows.getFirst();
    }

    @Override public void append(LabQuotedPaymentReceipt receipt) {
        jdbc.update("""
                INSERT INTO lab_quoted_payment_receipt(payment_request_id,transaction_id,quote_id,actor_account_id,
                    idempotency_key,amount,currency,payment_method,quote_fingerprint,command_fingerprint,clearance_id,completed_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, receipt.paymentRequestId(), receipt.transactionId(), receipt.quoteId(), receipt.actorAccountId(),
                receipt.idempotencyKey(), receipt.amount(), receipt.currency(), receipt.paymentMethod().name(),
                receipt.quoteFingerprint(), receipt.commandFingerprint(), receipt.clearanceId(), Timestamp.from(receipt.completedAt()));
    }

    private OutpatientQuote readQuote(String snapshot) {
        try { return mapper.readValue(snapshot, OutpatientQuote.class); }
        catch (Exception invalid) { throw new org.springframework.dao.DataIntegrityViolationException("Invalid stored Lab quote", invalid); }
    }

    private static LabQuotedPaymentReceipt receipt(ResultSet rs, int row) throws SQLException {
        return new LabQuotedPaymentReceipt(rs.getObject("payment_request_id", UUID.class),
                rs.getObject("transaction_id", UUID.class), rs.getObject("quote_id", UUID.class),
                rs.getObject("actor_account_id", UUID.class), rs.getString("idempotency_key"), rs.getBigDecimal("amount"),
                rs.getString("currency").trim(), com.mediflow.billing.domain.model.PaymentMethod.valueOf(rs.getString("payment_method")),
                rs.getString("quote_fingerprint"), rs.getString("command_fingerprint"),
                rs.getObject("clearance_id", UUID.class), rs.getTimestamp("completed_at").toInstant());
    }

    private static UUID[] labIds(ResultSet rs) throws SQLException {
        var array = rs.getArray("lab_test_ids");
        return array == null ? new UUID[0] : (UUID[]) array.getArray();
    }

    /** PostgreSQL TIMESTAMPTZ stores microseconds; quote JSON retains the original nanosecond clock value. */
    private static boolean sameDatabaseInstant(Instant left, Instant right) {
        return left != null && right != null
                && java.time.Duration.between(left, right).abs().compareTo(java.time.Duration.ofNanos(1_000)) < 0;
    }

    private static void fail(String code) { throw new BillingRuleException(code, code); }

    private record BindingRow(UUID labId, UUID sourceOrderId, UUID sourcePatient, UUID sourceDepartment,
            UUID recordId, String sourceEpisodeType, UUID sourceEpisodeId, String sourcePriceCode, String labType,
            String purpose, String status, BigDecimal requestedAmount, String requestCurrency, UUID accountId,
            UUID patientId, UUID departmentId, String careEpisodeType, UUID careEpisodeId, String accountCurrency,
            UUID targetRecord, UUID[] labIds, BigDecimal linkedAmount, String sourceType, UUID sourceId,
            String priceCode, String description, String chargeStatus, BigDecimal quantity, BigDecimal unitAmount, BigDecimal grossAmount,
            UUID chargePatient, UUID chargeDepartment, UUID quoteId, UUID quotePatient, UUID quoteEpisodeId,
            BigDecimal patientPayable, Instant quoteExpiresAt, OutpatientQuote quote, UUID currentQuoteId) { }
}
