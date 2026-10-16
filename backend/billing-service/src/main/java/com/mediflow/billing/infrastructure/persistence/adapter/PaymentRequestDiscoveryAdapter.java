package com.mediflow.billing.infrastructure.persistence.adapter;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.billing.application.dto.command.PaymentRequestSourceQuery;
import com.mediflow.billing.application.dto.response.PaymentRequestDiscoveryDTO;
import com.mediflow.billing.application.port.out.PaymentRequestDiscoveryPort;

/**
 * Exact-match discovery for L1 (HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md §3). Every query
 * joins from the exact owning source table (one row per sourceId, PK-enforced) out to
 * PAYMENT_REQUEST/BILLING_ACCOUNT/PAYMENT_REQUEST_TARGET with LEFT JOIN, so the SQL itself can
 * never fan out to more than one row; a broken downstream link surfaces as NULL columns instead,
 * which {@link #resolve} turns into a 503 rather than silently picking an arbitrary row.
 */
@Repository
public class PaymentRequestDiscoveryAdapter implements PaymentRequestDiscoveryPort {

    private static final String COMMON_SELECT = """
            SELECT pr.payment_request_id, pr.invoice_id, pr.account_id, pr.purpose AS request_purpose,
                   pr.status AS request_status, pr.requested_amount, pr.currency, pr.expires_at,
                   ba.patient_id, ba.department_id, ba.care_episode_type, ba.care_episode_id, ba.status AS account_status,
                   t.appointment_id, t.record_id, t.prescription_id, t.lab_test_ids,
                   (SELECT COALESCE(SUM(p.amount), 0) FROM PAYMENT_TRANSACTION p
                       WHERE p.payment_request_id = pr.payment_request_id
                       AND p.transaction_type = 'PAYMENT' AND p.status = 'COMPLETED') AS paid_amount,
                   statement_timestamp() AS observed_at,
            """;

    private final JdbcTemplate jdbc;

    public PaymentRequestDiscoveryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true, timeout = 3)
    public Optional<PaymentRequestDiscoveryDTO> findExact(PaymentRequestSourceQuery query) {
        return switch (query.purpose()) {
            case EXAM -> resolve(jdbc.query(COMMON_SELECT + """
                    TRUE AS source_issued
                    FROM exam_charge_source s
                    LEFT JOIN PAYMENT_REQUEST pr ON pr.payment_request_id = s.payment_request_id
                    LEFT JOIN BILLING_ACCOUNT ba ON ba.account_id = pr.account_id
                    LEFT JOIN PAYMENT_REQUEST_TARGET t ON t.payment_request_id = pr.payment_request_id
                    WHERE s.appointment_id = ?
                    """, ROW_MAPPER, query.sourceId()), query, "EXAM");
            case LAB_TEST -> resolve(jdbc.query(COMMON_SELECT + """
                    TRUE AS source_issued
                    FROM lab_test_charge_source s
                    LEFT JOIN PAYMENT_REQUEST pr ON pr.payment_request_id = s.payment_request_id
                    LEFT JOIN BILLING_ACCOUNT ba ON ba.account_id = pr.account_id
                    LEFT JOIN PAYMENT_REQUEST_TARGET t ON t.payment_request_id = pr.payment_request_id
                    WHERE s.lab_id = ?
                    """, ROW_MAPPER, query.sourceId()), query, "LAB_TEST");
            // care_context='ADMISSION' legitimately has no outpatient request (ck_prescription_charge_request):
            // that is a "no source for this boundary" 404, not corruption, so it is read from the source
            // row itself (source_issued), never inferred from the LEFT JOIN coming back empty.
            case PRESCRIPTION -> resolve(jdbc.query(COMMON_SELECT + """
                    (s.care_context = 'OUTPATIENT' AND s.payment_request_id IS NOT NULL) AS source_issued
                    FROM prescription_charge_source s
                    LEFT JOIN PAYMENT_REQUEST pr ON pr.payment_request_id = s.payment_request_id
                    LEFT JOIN BILLING_ACCOUNT ba ON ba.account_id = pr.account_id
                    LEFT JOIN PAYMENT_REQUEST_TARGET t ON t.payment_request_id = pr.payment_request_id
                    WHERE s.prescription_id = ?
                    """, ROW_MAPPER, query.sourceId()), query, "PRESCRIPTION");
        };
    }

    private Optional<PaymentRequestDiscoveryDTO> resolve(List<Row> rows, PaymentRequestSourceQuery query, String purposeName) {
        if (rows.isEmpty()) return Optional.empty();
        Row row = rows.getFirst();
        if (!row.sourceIssued()) return Optional.empty();
        // lab_test_ids is NOT NULL DEFAULT '{}' on PAYMENT_REQUEST_TARGET, so a null array here can only
        // mean the LEFT JOIN found no target row at all — every issuer writes one in the same transaction
        // as the request, so a missing row is corruption, never a legitimate empty value.
        if (row.paymentRequestId() == null || row.accountId() == null || row.careEpisodeType() == null
                || row.labTestIds() == null) {
            throw new DataIntegrityViolationException(
                    "Payment request mapping broken for " + purposeName + " source " + query.sourceId());
        }
        if (!purposeName.equals(row.requestPurpose())) {
            throw new DataIntegrityViolationException(
                    "Payment request purpose mismatch for " + purposeName + " source " + query.sourceId());
        }
        if (!query.patientId().equals(row.patientId())
                || !"OUTPATIENT_VISIT".equals(row.careEpisodeType())
                || !query.careEpisodeId().equals(row.careEpisodeId())) {
            return Optional.empty();
        }
        BigDecimal requested = row.requestedAmount();
        BigDecimal paid = row.paidAmount() == null ? BigDecimal.ZERO : row.paidAmount();
        BigDecimal remaining = requested.subtract(paid).max(BigDecimal.ZERO);
        List<UUID> labTestIds = row.labTestIds() == null ? List.of() : Arrays.asList(row.labTestIds());
        return Optional.of(new PaymentRequestDiscoveryDTO(row.paymentRequestId(), row.invoiceId(), row.accountId(),
                row.patientId(), row.departmentId(), purposeName, purposeName, query.sourceId(), row.careEpisodeId(),
                "OUTPATIENT_VISIT", row.appointmentId(), row.recordId(), row.prescriptionId(), labTestIds,
                requested, paid, remaining, row.currency() == null ? null : row.currency().trim(),
                row.requestStatus(), row.accountStatus(),
                row.expiresAt() == null ? null : row.expiresAt().toInstant(), row.observedAt().toInstant()));
    }

    private record Row(UUID paymentRequestId, UUID invoiceId, UUID accountId, String requestPurpose,
                        String requestStatus, BigDecimal requestedAmount, String currency, Timestamp expiresAt,
                        UUID patientId, UUID departmentId, String careEpisodeType, UUID careEpisodeId,
                        String accountStatus, UUID appointmentId, UUID recordId, UUID prescriptionId,
                        UUID[] labTestIds, BigDecimal paidAmount, Timestamp observedAt, boolean sourceIssued) {
    }

    private static final RowMapper<Row> ROW_MAPPER = (ResultSet rs, int i) -> new Row(
            uuid(rs, "payment_request_id"), uuid(rs, "invoice_id"), uuid(rs, "account_id"),
            rs.getString("request_purpose"), rs.getString("request_status"), rs.getBigDecimal("requested_amount"),
            rs.getString("currency"), rs.getTimestamp("expires_at"), uuid(rs, "patient_id"), uuid(rs, "department_id"),
            rs.getString("care_episode_type"), uuid(rs, "care_episode_id"), rs.getString("account_status"),
            uuid(rs, "appointment_id"), uuid(rs, "record_id"), uuid(rs, "prescription_id"), labTestIds(rs),
            rs.getBigDecimal("paid_amount"), rs.getTimestamp("observed_at"), rs.getBoolean("source_issued"));

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private static UUID[] labTestIds(ResultSet rs) throws SQLException {
        var array = rs.getArray("lab_test_ids");
        return array == null ? null : (UUID[]) array.getArray();
    }
}
