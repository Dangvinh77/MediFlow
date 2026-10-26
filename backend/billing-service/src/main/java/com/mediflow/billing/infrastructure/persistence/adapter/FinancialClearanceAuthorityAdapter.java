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
                    CASE WHEN f.purpose IN ('PRESCRIPTION','EXAM','LAB_TEST') THEN (
                        SELECT o.payload::jsonb->>'occurredAt' FROM BILLING_EVENT_OUTBOX o
                        WHERE o.aggregate_id=f.account_id AND o.routing_key='financial.clearance.granted'
                            AND o.payload::jsonb->'payload'->>'clearanceId'=f.clearance_id::text
                        ORDER BY o.created_at, o.event_id LIMIT 1
                    ) END AS source_granted_at,
                    COALESCE(r.purpose=f.purpose AND r.status='PAID'
                    AND a.status NOT IN ('CLOSED','SETTLED') AND NOT f.emergency_override
                    AND r.account_id=f.account_id AND r.invoice_id=f.invoice_id
                    AND a.patient_id=f.patient_id AND a.care_episode_type=f.care_episode_type
                    AND a.care_episode_id=f.care_episode_id AND a.currency=f.currency AND r.currency=f.currency
                    AND f.amount=r.requested_amount
                    AND (r.requested_amount>0 OR (r.requested_amount=0 AND f.payment_method='ZERO_LIABILITY'
                    AND (
                        (f.purpose='EXAM' AND EXISTS (
                            SELECT 1
                              FROM scheduled_payment_operation o
                              JOIN outpatient_quote q ON q.payment_request_id=o.payment_request_id
                                  AND q.quote_id=o.quote_id
                              JOIN PAYMENT_REQUEST_CHARGE qrc ON qrc.payment_request_id=o.payment_request_id
                              JOIN CHARGE qc ON qc.charge_id=qrc.charge_id
                             WHERE o.payment_request_id=r.payment_request_id
                               AND o.account_id=f.account_id AND o.patient_id=f.patient_id
                               AND o.registration_id=f.record_id AND o.source_type='EXAM'
                               AND o.source_id=f.record_id AND o.care_episode_id=f.care_episode_id
                               AND o.quote_id IS NOT NULL AND o.basis='ZERO_LIABILITY'
                               AND o.status='COMPLETED' AND o.amount=0 AND o.transaction_id IS NULL
                               AND o.clearance_id=f.clearance_id
                               AND abs(extract(epoch FROM (o.completed_at-f.granted_at))) <= 0.000001
                               AND o.fence_token>0
                               AND q.patient_id=f.patient_id AND q.care_episode_id=f.care_episode_id
                               AND q.patient_payable=0 AND q.expires_at=r.expires_at
                               AND q.snapshot->'lines' IS NOT NULL
                               AND CASE WHEN jsonb_typeof(q.snapshot->'lines')='array'
                                        THEN jsonb_array_length(q.snapshot->'lines')=1 ELSE FALSE END
                               AND q.snapshot #>> '{lines,0,sourceId}'=f.record_id::text
                               AND q.snapshot #>> '{lines,0,sourceType}'='EXAM'
                               AND q.snapshot #>> '{lines,0,priceCode}'=qc.price_code
                               AND qc.source_type='EXAM' AND qc.source_id=f.record_id
                               AND qc.account_id=f.account_id AND qc.patient_id=f.patient_id
                               AND qc.department_id=a.department_id AND qc.status='POSTED'
                               AND qc.quantity=(q.snapshot #>> '{lines,0,quantity}')::numeric
                               AND qc.unit_amount=(q.snapshot #>> '{lines,0,unitPrice}')::numeric
                               AND qc.gross_amount=(q.snapshot #>> '{lines,0,grossAmount}')::numeric
                               AND qrc.requested_amount=0
                               AND NOT EXISTS (SELECT 1 FROM PAYMENT_TRANSACTION p
                                   WHERE p.payment_request_id=r.payment_request_id)
                    )))
                        OR (f.purpose='LAB_TEST' AND EXISTS (
                            SELECT 1
                              FROM lab_zero_payable_eligibility l
                              JOIN outpatient_quote q ON q.payment_request_id=l.payment_request_id
                                  AND q.quote_id=l.quote_id
                              JOIN lab_test_charge_source s ON s.payment_request_id=l.payment_request_id
                              JOIN PAYMENT_REQUEST_TARGET lt ON lt.payment_request_id=l.payment_request_id
                              JOIN PAYMENT_REQUEST_CHARGE lrc ON lrc.payment_request_id=l.payment_request_id
                              JOIN CHARGE lc ON lc.charge_id=lrc.charge_id
                             WHERE l.payment_request_id=r.payment_request_id
                               AND l.quote_id IS NOT NULL AND l.basis='ZERO_LIABILITY'
                               AND l.clearance_id=f.clearance_id
                               AND abs(extract(epoch FROM (l.completed_at-f.granted_at))) <= 0.000001
                               AND q.patient_id=f.patient_id AND q.care_episode_id=f.care_episode_id
                               AND q.patient_payable=0 AND q.expires_at=r.expires_at
                               AND s.lab_id=f.lab_test_ids[1] AND s.patient_id=f.patient_id
                               AND s.department_id=a.department_id
                               AND s.care_episode_type=f.care_episode_type
                               AND s.care_episode_id=f.care_episode_id
                               AND lt.record_id=s.record_id AND lt.lab_test_ids=ARRAY[s.lab_id]
                               AND lc.source_type='LAB_TEST' AND lc.source_id=s.lab_id
                               AND lc.price_code=s.price_code AND lc.account_id=f.account_id
                               AND lc.patient_id=f.patient_id AND lc.department_id=a.department_id
                               AND lc.status='POSTED' AND lc.quantity=(q.snapshot #>> '{lines,0,quantity}')::numeric
                               AND lc.unit_amount=(q.snapshot #>> '{lines,0,unitPrice}')::numeric
                               AND lc.gross_amount=(q.snapshot #>> '{lines,0,grossAmount}')::numeric
                               AND q.snapshot->'lines' IS NOT NULL
                               AND CASE WHEN jsonb_typeof(q.snapshot->'lines')='array'
                                        THEN jsonb_array_length(q.snapshot->'lines')=1 ELSE FALSE END
                               AND q.snapshot #>> '{lines,0,sourceId}'=s.lab_id::text
                               AND q.snapshot #>> '{lines,0,sourceType}'='LAB_TEST'
                               AND q.snapshot #>> '{lines,0,priceCode}'=s.price_code
                               AND lrc.requested_amount=0
                               AND NOT EXISTS (SELECT 1 FROM PAYMENT_TRANSACTION p
                                   WHERE p.payment_request_id=r.payment_request_id)
                        ))
                       ))
                    AND (f.purpose<>'LAB_TEST' OR r.requested_amount=0
                      OR NOT EXISTS (SELECT 1 FROM outpatient_quote any_quote
                          WHERE any_quote.payment_request_id=r.payment_request_id)
                      OR EXISTS (
                        SELECT 1
                          FROM lab_quoted_payment_receipt lr
                          JOIN outpatient_quote lq ON lq.payment_request_id=lr.payment_request_id
                              AND lq.quote_id=lr.quote_id
                          JOIN lab_test_charge_source ls ON ls.payment_request_id=lr.payment_request_id
                          JOIN PAYMENT_REQUEST_TARGET ltarget ON ltarget.payment_request_id=lr.payment_request_id
                          JOIN PAYMENT_REQUEST_CHARGE lrc ON lrc.payment_request_id=lr.payment_request_id
                          JOIN CHARGE lc ON lc.charge_id=lrc.charge_id
                          JOIN PAYMENT_TRANSACTION lp ON lp.transaction_id=lr.transaction_id
                         WHERE lr.payment_request_id=r.payment_request_id
                           AND lr.clearance_id=f.clearance_id AND lr.amount=r.requested_amount
                           AND lr.currency=f.currency AND lr.payment_method=f.payment_method
                           AND lr.quote_id=lq.quote_id
                           AND abs(extract(epoch FROM (lr.completed_at-f.granted_at))) <= 0.000001
                           AND lr.idempotency_key=lp.idempotency_key AND lr.actor_account_id=lp.actor_account_id
                           AND lr.amount>0 AND lp.payment_request_id=r.payment_request_id
                           AND lp.status='COMPLETED' AND lp.transaction_type='PAYMENT'
                           AND lp.classification='SERVICE_PAYMENT' AND lp.amount=lr.amount
                           AND lp.currency=lr.currency AND lp.payment_method=lr.payment_method
                           AND abs(extract(epoch FROM (lp.completed_at-lr.completed_at))) <= 0.000001
                           AND lq.patient_id=f.patient_id AND lq.care_episode_id=f.care_episode_id
                           AND lq.patient_payable=r.requested_amount AND lq.expires_at=r.expires_at
                           AND ls.lab_id=f.lab_test_ids[1] AND ls.patient_id=f.patient_id
                           AND ls.department_id=a.department_id AND ls.care_episode_type=f.care_episode_type
                           AND ls.care_episode_id=f.care_episode_id AND ls.source_order_id IS NOT NULL
                           AND ls.lab_type IS NOT NULL AND ls.price_code=lc.price_code
                           AND ls.lab_id=lc.source_id AND lc.source_type='LAB_TEST'
                           AND lc.account_id=f.account_id AND lc.patient_id=f.patient_id
                           AND lc.department_id=a.department_id AND lc.status='POSTED'
                           AND lc.description=(lq.snapshot #>> '{lines,0,description}')
                           AND lc.quantity=(lq.snapshot #>> '{lines,0,quantity}')::numeric
                           AND lc.unit_amount=(lq.snapshot #>> '{lines,0,unitPrice}')::numeric
                           AND lc.gross_amount=(lq.snapshot #>> '{lines,0,grossAmount}')::numeric
                           AND ltarget.record_id=ls.record_id AND ltarget.lab_test_ids=ARRAY[ls.lab_id]
                           AND lrc.requested_amount=lr.amount
                           AND lq.snapshot->'lines' IS NOT NULL
                           AND CASE WHEN jsonb_typeof(lq.snapshot->'lines')='array'
                                THEN jsonb_array_length(lq.snapshot->'lines')=1 ELSE FALSE END
                           AND lq.snapshot #>> '{lines,0,sourceId}'=ls.lab_id::text
                           AND lq.snapshot #>> '{lines,0,sourceType}'='LAB_TEST'
                           AND lq.snapshot #>> '{lines,0,priceCode}'=ls.price_code
                      ))
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
                    AND cardinality(t.lab_test_ids)=0 AND cardinality(f.lab_test_ids)=0)
                    OR (f.purpose='EXAM' AND f.care_episode_type='OUTPATIENT_VISIT'
                    AND f.appointment_id IS NOT NULL AND f.care_episode_id=f.appointment_id
                    AND t.appointment_id=f.appointment_id AND t.record_id IS NULL AND f.record_id IS NULL
                    AND t.prescription_id IS NULL AND f.prescription_id IS NULL
                    AND t.admission_id IS NULL AND f.admission_id IS NULL
                    AND t.surgery_case_id IS NULL AND f.surgery_case_id IS NULL
                    AND cardinality(t.lab_test_ids)=0 AND cardinality(f.lab_test_ids)=0
                    AND EXISTS (SELECT 1 FROM exam_charge_source s WHERE s.appointment_id=f.appointment_id
                        AND s.payment_request_id=r.payment_request_id AND s.patient_id=f.patient_id
                        AND s.care_episode_id=f.care_episode_id AND s.department_id=a.department_id))
                    OR (f.purpose='EXAM' AND f.care_episode_type='OUTPATIENT_VISIT'
                    AND f.appointment_id IS NULL AND f.record_id IS NOT NULL AND f.care_episode_id=f.record_id
                    AND t.appointment_id IS NULL AND t.record_id=f.record_id
                    AND t.prescription_id IS NULL AND f.prescription_id IS NULL
                    AND t.admission_id IS NULL AND f.admission_id IS NULL
                    AND t.surgery_case_id IS NULL AND f.surgery_case_id IS NULL
                    AND cardinality(t.lab_test_ids)=0 AND cardinality(f.lab_test_ids)=0
                    AND EXISTS (
                        SELECT 1
                          FROM outpatient_registration_source s
                          JOIN scheduled_payment_operation o ON o.payment_request_id=s.payment_request_id
                          JOIN PAYMENT_REQUEST_CHARGE erc ON erc.payment_request_id=o.payment_request_id
                          JOIN CHARGE ec ON ec.charge_id=erc.charge_id
                          LEFT JOIN outpatient_quote eq ON eq.payment_request_id=o.payment_request_id
                              AND eq.quote_id=o.quote_id
                         WHERE s.registration_id=f.record_id AND s.payment_request_id=r.payment_request_id
                           AND s.account_id=f.account_id AND s.patient_id=f.patient_id
                           AND s.department_id=a.department_id AND s.source_type='EXAM'
                           AND s.source_id=f.record_id AND s.care_episode_type=f.care_episode_type
                           AND s.care_episode_id=f.care_episode_id
                           AND o.account_id=f.account_id AND o.registration_id=f.record_id
                           AND o.reservation_id=s.reservation_id AND o.patient_id=f.patient_id
                           AND o.source_type='EXAM' AND o.source_id=f.record_id
                           AND o.care_episode_id=f.care_episode_id AND o.status='COMPLETED'
                           AND o.clearance_id=f.clearance_id
                           AND abs(extract(epoch FROM (o.completed_at-f.granted_at))) <= 0.000001
                           AND ((o.basis='PATIENT_PAYMENT' AND o.amount>0 AND o.transaction_id IS NOT NULL
                                 AND o.amount=r.requested_amount)
                             OR (o.basis='ZERO_LIABILITY' AND o.amount=0 AND o.transaction_id IS NULL
                                 AND r.requested_amount=0 AND o.quote_id IS NOT NULL))
                           AND ec.source_type='EXAM' AND ec.source_id=f.record_id AND ec.price_code=s.price_code
                           AND ec.account_id=f.account_id AND ec.patient_id=f.patient_id
                           AND ec.department_id=a.department_id AND ec.status='POSTED'
                           AND ec.quantity=1 AND ec.unit_amount=ec.gross_amount
                           AND erc.requested_amount=r.requested_amount
                           AND ((o.quote_id IS NULL AND o.basis='PATIENT_PAYMENT')
                             OR (eq.quote_id IS NOT NULL AND eq.patient_id=f.patient_id
                                 AND eq.care_episode_id=f.care_episode_id
                                 AND eq.patient_payable=r.requested_amount AND eq.expires_at=r.expires_at
                                 AND eq.snapshot->'lines' IS NOT NULL
                                 AND CASE WHEN jsonb_typeof(eq.snapshot->'lines')='array'
                                      THEN jsonb_array_length(eq.snapshot->'lines')=1 ELSE FALSE END
                                 AND eq.snapshot #>> '{lines,0,sourceId}'=f.record_id::text
                                 AND eq.snapshot #>> '{lines,0,sourceType}'='EXAM'
                                 AND eq.snapshot #>> '{lines,0,priceCode}'=s.price_code
                                 AND ec.quantity=(eq.snapshot #>> '{lines,0,quantity}')::numeric
                                 AND ec.unit_amount=(eq.snapshot #>> '{lines,0,unitPrice}')::numeric
                                 AND ec.gross_amount=(eq.snapshot #>> '{lines,0,grossAmount}')::numeric))
                    ))
                    OR (f.purpose='LAB_TEST' AND cardinality(f.lab_test_ids)=1
                    AND t.lab_test_ids=f.lab_test_ids AND t.record_id IS NOT DISTINCT FROM f.record_id
                    AND t.appointment_id IS NULL AND f.appointment_id IS NULL
                    AND t.prescription_id IS NULL AND f.prescription_id IS NULL
                    AND t.admission_id IS NULL AND f.admission_id IS NULL
                    AND t.surgery_case_id IS NULL AND f.surgery_case_id IS NULL
                    AND EXISTS (SELECT 1 FROM lab_test_charge_source s WHERE s.lab_id=f.lab_test_ids[1]
                        AND s.payment_request_id=r.payment_request_id AND s.patient_id=f.patient_id
                        AND s.record_id IS NOT DISTINCT FROM f.record_id AND s.care_episode_type=f.care_episode_type
                        AND s.care_episode_id=f.care_episode_id AND s.department_id=a.department_id)))
                    AND (f.purpose NOT IN ('EXAM','LAB_TEST') OR
                        (SELECT COUNT(*) FROM PAYMENT_REQUEST_CHARGE x WHERE x.payment_request_id=r.payment_request_id)=1)
                    AND EXISTS(SELECT 1 FROM PAYMENT_REQUEST_CHARGE x WHERE x.payment_request_id=r.payment_request_id)
                    AND NOT EXISTS(SELECT 1 FROM PAYMENT_REQUEST_CHARGE x WHERE x.payment_request_id=r.payment_request_id
                        AND (SELECT COALESCE(SUM(CASE WHEN p.transaction_type='PAYMENT' THEN pa.amount ELSE -pa.amount END),0)
                            FROM PAYMENT_ALLOCATION pa JOIN PAYMENT_TRANSACTION p ON p.transaction_id=pa.transaction_id
                            WHERE pa.charge_id=x.charge_id AND p.payment_request_id=r.payment_request_id
                                AND p.status='COMPLETED' AND p.account_id=f.account_id AND p.currency=f.currency
                                AND p.classification='SERVICE_PAYMENT'
                                AND p.transaction_type IN ('PAYMENT','REFUND','REVERSAL'))<x.requested_amount)
                    AND NOT EXISTS(SELECT 1 FROM PAYMENT_REQUEST_CHARGE x JOIN CHARGE c ON c.charge_id=x.charge_id
                        WHERE x.payment_request_id=r.payment_request_id AND
                        (c.status<>'POSTED' OR c.source_type<>f.purpose
                        OR c.source_id<>CASE WHEN f.purpose='SURGERY' THEN f.surgery_case_id
                            WHEN f.purpose='PRESCRIPTION' THEN f.prescription_id
                            WHEN f.purpose='EXAM' THEN f.appointment_id ELSE f.lab_test_ids[1] END
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
                rs.getBoolean("ledger_eligible"), rs.getTimestamp("observed_at").toInstant(),
                rs.getObject("appointment_id", UUID.class), rs.getObject("record_id", UUID.class), labIds(rs),
                rs.getObject("prescription_id", UUID.class)), id).stream().findFirst();
    }

    private static java.util.List<UUID> labIds(java.sql.ResultSet rs) throws java.sql.SQLException {
        var value = rs.getArray("lab_test_ids");
        if (value == null) return java.util.List.of();
        return java.util.Arrays.asList((UUID[]) value.getArray());
    }
}
