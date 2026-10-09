package com.mediflow.billing.infrastructure.persistence.adapter;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.billing.application.port.out.LedgerPaymentRepositoryPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.*;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class LedgerPaymentRepositoryAdapter implements LedgerPaymentRepositoryPort {
    private final JdbcTemplate jdbc;

    public LedgerPaymentRepositoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void lockIdempotencyKey(String key) {
        jdbc.execute("SET LOCAL lock_timeout = '3s'");
        // Cross-account key reuse must serialize before acquiring an account lock.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", key);
    }

    @Override
    public PaymentContext lockRequest(UUID requestId) {
        var accounts = jdbc.queryForList("SELECT account_id FROM PAYMENT_REQUEST WHERE payment_request_id = ?", UUID.class, requestId);
        if (accounts.isEmpty()) throw new com.mediflow.billing.domain.exception.LedgerPaymentRequestNotFoundException(requestId);
        var account = jdbc.query("SELECT * FROM BILLING_ACCOUNT WHERE account_id = ? FOR UPDATE", (rs, row) ->
                BillingAccount.restore(uuid(rs, "account_id"), uuid(rs, "patient_id"), uuid(rs, "department_id"),
                        CareEpisodeType.valueOf(rs.getString("care_episode_type")), uuid(rs, "care_episode_id"),
                        AccountStatus.valueOf(rs.getString("status")), rs.getString("currency").trim(), rs.getLong("version"),
                        instant(rs, "opened_at"), instant(rs, "charge_closed_at"), instant(rs, "closed_at"),
                        instant(rs, "created_at"), instant(rs, "updated_at")), accounts.getFirst()).getFirst();
        var request = jdbc.query("SELECT * FROM PAYMENT_REQUEST WHERE payment_request_id = ? FOR UPDATE",
                (rs, row) -> PaymentRequest.restore(uuid(rs, "payment_request_id"), uuid(rs, "invoice_id"), uuid(rs, "account_id"),
                        PaymentRequestPurpose.valueOf(rs.getString("purpose")), PaymentRequestStatus.valueOf(rs.getString("status")),
                        rs.getBigDecimal("requested_amount"), rs.getString("currency").trim(), instant(rs, "expires_at"),
                        uuid(rs, "created_by"), instant(rs, "created_at"), instant(rs, "completed_at")), requestId).getFirst();
        var targets = jdbc.query("SELECT * FROM PAYMENT_REQUEST_TARGET WHERE payment_request_id = ?", (rs, row) ->
                new ClearanceTarget(uuid(rs, "appointment_id"), uuid(rs, "record_id"),
                        Arrays.asList((UUID[]) rs.getArray("lab_test_ids").getArray()), uuid(rs, "prescription_id"),
                        uuid(rs, "admission_id"), uuid(rs, "surgery_case_id")), requestId);
        BigDecimal paid = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN transaction_type = 'PAYMENT' THEN amount ELSE -amount END), 0)
                  FROM PAYMENT_TRANSACTION WHERE payment_request_id = ? AND status = 'COMPLETED'
                """, BigDecimal.class, requestId);
        ClearanceTarget target = targets.isEmpty() ? null : targets.getFirst();
        return new PaymentContext(account, request, target, paid);
    }

    @Override
    public void verifyRequestTargets(PaymentContext context) {
        var account = context.account();
        var request = context.request();
        var target = context.target();
        if (request.getPurpose() != PaymentRequestPurpose.SETTLEMENT) {
            if (target == null) throw rule("BILLING_CLEARANCE_TARGET_MISSING");
            target.validate(ClearancePurpose.valueOf(request.getPurpose().name()),
                    account.getCareEpisodeType(), account.getCareEpisodeId());
        }
        var sources = jdbc.query("""
                SELECT c.source_type, c.source_id, c.account_id, c.patient_id, c.status, rc.requested_amount
                  FROM PAYMENT_REQUEST_CHARGE rc JOIN CHARGE c ON c.charge_id = rc.charge_id
                 WHERE rc.payment_request_id = ?
                """, (rs, row) -> new ChargeSource(rs.getString("source_type"), uuid(rs, "source_id"),
                        uuid(rs, "account_id"), uuid(rs, "patient_id"), rs.getString("status"),
                        rs.getBigDecimal("requested_amount")), request.getPaymentRequestId());
        if (request.getPurpose() == PaymentRequestPurpose.ADMISSION_DEPOSIT) {
            if (!sources.isEmpty()) throw rule("BILLING_DEPOSIT_CANNOT_ALLOCATE_CHARGES");
            return;
        }
        if (sources.isEmpty() || sources.stream().anyMatch(source -> !account.getAccountId().equals(source.accountId())
                || !account.getPatientId().equals(source.patientId()) || !"POSTED".equals(source.status()))
                || sources.stream().map(ChargeSource::amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .compareTo(request.getRequestedAmount()) != 0) throw rule("BILLING_REQUEST_CHARGE_MISMATCH");
        if (request.getPurpose() == PaymentRequestPurpose.SETTLEMENT) {
            if (account.getCareEpisodeType() != CareEpisodeType.ADMISSION) throw rule("BILLING_SETTLEMENT_EPISODE_MISMATCH");
            return;
        }
        if (target == null) throw rule("BILLING_CLEARANCE_TARGET_MISSING");
        var sourceIds = sources.stream().map(ChargeSource::sourceId).collect(java.util.stream.Collectors.toSet());
        boolean matches = switch (request.getPurpose()) {
            case LAB_TEST -> sources.stream().allMatch(source -> "LAB_TEST".equals(source.type()))
                    && sourceIds.equals(new java.util.HashSet<>(target.labTestIds()));
            case PRESCRIPTION -> sources.stream().allMatch(source -> "PRESCRIPTION".equals(source.type()))
                    && sourceIds.equals(java.util.Set.of(target.prescriptionId()));
            case SURGERY -> sources.stream().allMatch(source -> "SURGERY".equals(source.type()))
                    && sourceIds.equals(java.util.Set.of(target.surgeryCaseId()));
            case EXAM -> sources.stream().allMatch(source -> "EXAM".equals(source.type()))
                    && sourceIds.equals(java.util.Set.of(target.recordId() == null ? target.appointmentId() : target.recordId()));
            default -> false;
        };
        if (!matches) throw rule("BILLING_REQUEST_SOURCE_TARGET_MISMATCH");
    }

    private record ChargeSource(String type, UUID sourceId, UUID accountId, UUID patientId, String status, BigDecimal amount) { }

    @Override
    public Optional<RecordedPayment> findByIdempotencyKey(String key) {
        return jdbc.query("SELECT * FROM PAYMENT_TRANSACTION WHERE idempotency_key = ?", (rs, row) ->
                new RecordedPayment(transaction(rs), uuid(rs, "actor_account_id")), key).stream().findFirst();
    }

    @Override
    public void save(PaymentTransaction transaction, PaymentRequest request, UUID actor) {
        jdbc.update("""
                INSERT INTO PAYMENT_TRANSACTION(transaction_id,account_id,payment_request_id,transaction_type,
                    classification,status,amount,currency,payment_method,provider_reference,idempotency_key,
                    original_transaction_id,completed_at,created_at,actor_account_id)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, transaction.getTransactionId(), transaction.getAccountId(), transaction.getPaymentRequestId(),
                transaction.getTransactionType().name(), transaction.getClassification().name(), transaction.getStatus().name(),
                transaction.getAmount(), transaction.getCurrency(), transaction.getPaymentMethod(), transaction.getProviderReference(),
                transaction.getIdempotencyKey(), transaction.getOriginalTransactionId(), at(transaction.getCompletedAt()),
                at(transaction.getCreatedAt()), actor);
        jdbc.update("UPDATE PAYMENT_REQUEST SET status = ?, completed_at = ? WHERE payment_request_id = ?",
                request.getStatus().name(), at(request.getCompletedAt()), request.getPaymentRequestId());
    }

    @Override
    public FinancialClearance saveClearance(FinancialClearance clearance) {
        UUID id = UUID.randomUUID();
        // The domain cannot accidentally substitute the request ID for clearance identity.
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO FINANCIAL_CLEARANCE(clearance_id,account_id,payment_request_id,invoice_id,patient_id,
                        purpose,care_episode_type,care_episode_id,appointment_id,record_id,lab_test_ids,prescription_id,
                        admission_id,surgery_case_id,amount,currency,payment_method,emergency_override,expires_at,granted_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """);
            Object[] values = {id, clearance.getAccountId(), clearance.getPaymentRequestId(), clearance.getInvoiceId(),
                    clearance.getPatientId(), clearance.getPurpose().name(), clearance.getCareEpisodeType().name(),
                    clearance.getCareEpisodeId(), clearance.getAppointmentId(), clearance.getRecordId(),
                    connection.createArrayOf("uuid", clearance.getLabTestIds().toArray()), clearance.getPrescriptionId(),
                    clearance.getAdmissionId(), clearance.getSurgeryCaseId(), clearance.getAmount(), clearance.getCurrency(),
                    clearance.getPaymentMethod(), clearance.isEmergencyOverride(), at(clearance.getExpiresAt()), at(clearance.getGrantedAt())};
            for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
            return statement;
        });
        return FinancialClearance.restore(id, clearance.getAccountId(), clearance.getPaymentRequestId(), clearance.getInvoiceId(),
                clearance.getPatientId(), clearance.getPurpose(), clearance.getCareEpisodeType(), clearance.getCareEpisodeId(),
                clearance.getAppointmentId(), clearance.getRecordId(), clearance.getLabTestIds(), clearance.getPrescriptionId(),
                clearance.getAdmissionId(), clearance.getSurgeryCaseId(), clearance.getAmount(), clearance.getCurrency(),
                clearance.getPaymentMethod(), false, clearance.getExpiresAt(), clearance.getGrantedAt(), null);
    }

    @Override
    public void allocate(PaymentTransaction transaction) {
        // Account lock covers every charge/request in this account. No concurrent over-allocation.
        List<AllocationBudget> budgets = jdbc.query("""
                SELECT c.charge_id, c.account_id, c.status,
                       LEAST(rc.requested_amount - COALESCE((
                         SELECT SUM(CASE WHEN t.transaction_type = 'PAYMENT' THEN a.amount ELSE -a.amount END)
                           FROM PAYMENT_ALLOCATION a JOIN PAYMENT_TRANSACTION t ON t.transaction_id = a.transaction_id
                          WHERE a.charge_id = c.charge_id AND t.payment_request_id = rc.payment_request_id
                            AND t.status = 'COMPLETED'), 0),
                       c.gross_amount - COALESCE((
                         SELECT SUM(CASE WHEN t.transaction_type = 'PAYMENT' THEN a.amount ELSE -a.amount END)
                           FROM PAYMENT_ALLOCATION a JOIN PAYMENT_TRANSACTION t ON t.transaction_id = a.transaction_id
                          WHERE a.charge_id = c.charge_id AND t.status = 'COMPLETED'), 0)) AS available
                  FROM PAYMENT_REQUEST_CHARGE rc JOIN CHARGE c ON c.charge_id = rc.charge_id
                 WHERE rc.payment_request_id = ? ORDER BY c.charge_id
                """, (rs, row) -> new AllocationBudget(uuid(rs, "charge_id"), uuid(rs, "account_id"),
                        rs.getString("status"), rs.getBigDecimal("available")), transaction.getPaymentRequestId());
        BigDecimal remaining = transaction.getAmount();
        for (var budget : budgets) {
            if (!transaction.getAccountId().equals(budget.accountId()) || !"POSTED".equals(budget.status()))
                throw rule("BILLING_ALLOCATION_CHARGE_MISMATCH");
            BigDecimal amount = remaining.min(budget.available().max(BigDecimal.ZERO));
            if (amount.signum() > 0) {
                jdbc.update("INSERT INTO PAYMENT_ALLOCATION(allocation_id,transaction_id,charge_id,amount) VALUES (?,?,?,?)",
                        UUID.randomUUID(), transaction.getTransactionId(), budget.chargeId(), amount);
                remaining = remaining.subtract(amount);
            }
        }
        if (remaining.signum() != 0) throw rule("BILLING_ALLOCATION_EXCEEDS_CHARGES");
    }

    private record AllocationBudget(UUID chargeId, UUID accountId, String status, BigDecimal available) { }

    private static PaymentTransaction transaction(ResultSet rs) throws SQLException {
        return PaymentTransaction.restore(uuid(rs, "transaction_id"), uuid(rs, "account_id"), uuid(rs, "payment_request_id"),
                PaymentTransactionType.valueOf(rs.getString("transaction_type")), PaymentClassification.valueOf(rs.getString("classification")),
                PaymentTransactionStatus.valueOf(rs.getString("status")), rs.getBigDecimal("amount"), rs.getString("currency").trim(),
                rs.getString("payment_method"), rs.getString("provider_reference"), rs.getString("idempotency_key"),
                uuid(rs, "original_transaction_id"), instant(rs, "completed_at"), instant(rs, "created_at"));
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException { return rs.getObject(column, UUID.class); }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
    private static Timestamp at(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static BillingRuleException rule(String code) { return new BillingRuleException(code, code); }
}
