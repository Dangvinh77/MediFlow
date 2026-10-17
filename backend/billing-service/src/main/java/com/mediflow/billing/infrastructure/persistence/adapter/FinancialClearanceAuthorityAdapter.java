package com.mediflow.billing.infrastructure.persistence.adapter;

import java.util.Optional;
import java.util.UUID;
import com.mediflow.billing.application.port.out.FinancialClearanceAuthorityPort;
import com.mediflow.billing.domain.model.FinancialClearanceAuthority;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class FinancialClearanceAuthorityAdapter implements FinancialClearanceAuthorityPort {
    private final JdbcTemplate jdbc;
    public FinancialClearanceAuthorityAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** One statement = one PostgreSQL MVCC snapshot, including refunds and charge state. */
    @Override @Transactional(readOnly = true, timeout = 3)
    public Optional<FinancialClearanceAuthority> find(UUID id) {
        return jdbc.query("""
                SELECT f.*, statement_timestamp() AS observed_at,
                    CASE WHEN f.purpose='PRESCRIPTION' THEN (
                        SELECT o.payload::jsonb->>'occurredAt' FROM BILLING_EVENT_OUTBOX o
                        WHERE o.aggregate_id=f.account_id AND o.routing_key='financial.clearance.granted'
                            AND o.payload::jsonb->'payload'->>'clearanceId'=f.clearance_id::text
                        ORDER BY o.created_at, o.event_id LIMIT 1
                    ) END AS source_granted_at,
                    COALESCE(f.purpose IN ('SURGERY','PRESCRIPTION') AND r.purpose=f.purpose AND r.status='PAID'
                    AND a.status NOT IN ('CLOSED','SETTLED') AND NOT f.emergency_override
                    AND r.account_id=f.account_id AND r.invoice_id=f.invoice_id
                    AND a.patient_id=f.patient_id AND a.care_episode_type=f.care_episode_type
                    AND a.care_episode_id=f.care_episode_id AND a.currency=f.currency AND r.currency=f.currency
                    AND f.amount=r.requested_amount AND r.requested_amount>0
                    AND ((f.purpose='SURGERY'
                    AND t.surgery_case_id=f.surgery_case_id AND t.admission_id IS NOT DISTINCT FROM f.admission_id
                    AND ((f.care_episode_type='ADMISSION' AND f.admission_id=f.care_episode_id)
                        OR (f.care_episode_type='OUTPATIENT_VISIT' AND f.admission_id IS NULL))
                    AND t.appointment_id IS NULL AND t.record_id IS NULL AND t.prescription_id IS NULL
                    AND cardinality(t.lab_test_ids)=0
                    AND f.appointment_id IS NULL AND f.record_id IS NULL AND f.prescription_id IS NULL
                    AND cardinality(f.lab_test_ids)=0)
                    OR (f.purpose='PRESCRIPTION' AND f.care_episode_type='OUTPATIENT_VISIT'
                    AND t.prescription_id=f.prescription_id AND t.record_id IS NOT DISTINCT FROM f.record_id
                    AND f.prescription_id IS NOT NULL
                    AND t.appointment_id IS NULL AND f.appointment_id IS NULL
                    AND t.admission_id IS NULL AND f.admission_id IS NULL
                    AND t.surgery_case_id IS NULL AND f.surgery_case_id IS NULL
                    AND cardinality(t.lab_test_ids)=0 AND cardinality(f.lab_test_ids)=0
                    AND NOT EXISTS(SELECT 1 FROM PAYMENT_REQUEST_CHARGE x WHERE x.payment_request_id=r.payment_request_id
                        AND (SELECT COALESCE(SUM(CASE WHEN p.transaction_type='PAYMENT' THEN pa.amount ELSE -pa.amount END),0)
                            FROM PAYMENT_ALLOCATION pa JOIN PAYMENT_TRANSACTION p ON p.transaction_id=pa.transaction_id
                            WHERE pa.charge_id=x.charge_id AND p.payment_request_id=r.payment_request_id
                                AND p.status='COMPLETED' AND p.account_id=f.account_id)<x.requested_amount)))
                    AND EXISTS(SELECT 1 FROM PAYMENT_REQUEST_CHARGE x WHERE x.payment_request_id=r.payment_request_id)
                    AND NOT EXISTS(SELECT 1 FROM PAYMENT_REQUEST_CHARGE x JOIN CHARGE c ON c.charge_id=x.charge_id
                        WHERE x.payment_request_id=r.payment_request_id AND
                        (c.status<>'POSTED' OR c.source_type<>f.purpose
                        OR c.source_id<>CASE WHEN f.purpose='SURGERY' THEN f.surgery_case_id ELSE f.prescription_id END
                        OR c.account_id<>f.account_id OR c.patient_id<>f.patient_id OR x.requested_amount>c.gross_amount))
                    AND (SELECT COALESCE(SUM(x.requested_amount),0) FROM PAYMENT_REQUEST_CHARGE x
                        WHERE x.payment_request_id=r.payment_request_id)=r.requested_amount
                    AND NOT EXISTS(SELECT 1 FROM PAYMENT_TRANSACTION p WHERE p.payment_request_id=r.payment_request_id
                        AND p.status='COMPLETED' AND (p.account_id<>f.account_id OR p.currency<>f.currency
                            OR p.classification<>'SERVICE_PAYMENT'))
                    AND (SELECT COALESCE(SUM(CASE WHEN p.transaction_type='PAYMENT' THEN p.amount ELSE -p.amount END),0)
                        FROM PAYMENT_TRANSACTION p WHERE p.payment_request_id=r.payment_request_id
                            AND p.status='COMPLETED')>=r.requested_amount, FALSE) AS ledger_eligible
                FROM FINANCIAL_CLEARANCE f
                LEFT JOIN PAYMENT_REQUEST r ON r.payment_request_id=f.payment_request_id
                LEFT JOIN BILLING_ACCOUNT a ON a.account_id=f.account_id
                LEFT JOIN PAYMENT_REQUEST_TARGET t ON t.payment_request_id=r.payment_request_id
                WHERE f.clearance_id=?
                """, (rs, row) -> new FinancialClearanceAuthority(rs.getObject("clearance_id", UUID.class),
                rs.getObject("invoice_id", UUID.class), rs.getObject("account_id", UUID.class),
                rs.getObject("patient_id", UUID.class), rs.getString("purpose"), rs.getString("care_episode_type"),
                rs.getObject("care_episode_id", UUID.class), rs.getObject("admission_id", UUID.class),
                rs.getObject("surgery_case_id", UUID.class), rs.getString("source_granted_at") == null
                        ? rs.getTimestamp("granted_at").toInstant()
                        : java.time.Instant.parse(rs.getString("source_granted_at")),
                rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant(),
                rs.getBoolean("ledger_eligible"), rs.getTimestamp("observed_at").toInstant()), id).stream().findFirst();
    }
}
