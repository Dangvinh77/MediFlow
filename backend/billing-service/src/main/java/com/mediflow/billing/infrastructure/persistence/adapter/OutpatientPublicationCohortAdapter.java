package com.mediflow.billing.infrastructure.persistence.adapter;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.billing.application.port.out.OutpatientMvpMode;
import com.mediflow.billing.application.port.out.OutpatientPublicationCohortPort;
import com.mediflow.billing.infrastructure.config.OutpatientPublicationProperties;

/**
 * Enrollment is a plain no-op whenever mode/flags/allowlist say no — that is the default (off)
 * path, never an error. Reads the just-saved PAYMENT_REQUEST/PAYMENT_REQUEST_TARGET in the same
 * MANDATORY transaction as the issuer's own save, so it sees exactly what that save wrote.
 */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class OutpatientPublicationCohortAdapter implements OutpatientPublicationCohortPort {
    private final JdbcTemplate jdbc;
    private final OutpatientMvpMode mode;
    private final OutpatientPublicationProperties properties;

    public OutpatientPublicationCohortAdapter(JdbcTemplate jdbc, OutpatientMvpMode mode,
            OutpatientPublicationProperties properties) {
        this.jdbc = jdbc;
        this.mode = mode;
        this.properties = properties;
    }

    @Override
    public void enrollIfEligible(UUID paymentRequestId) {
        if (paymentRequestId == null || !mode.enabled() || !properties.enabled()) return;
        List<java.util.Map<String, Object>> requests = jdbc.queryForList(
                "SELECT account_id, purpose FROM PAYMENT_REQUEST WHERE payment_request_id = ?", paymentRequestId);
        if (requests.isEmpty()) return;
        var request = requests.getFirst();
        String purpose = (String) request.get("purpose");
        if (!properties.purposes().contains(purpose)) return;
        UUID accountId = (UUID) request.get("account_id");
        var target = jdbc.queryForMap(
                "SELECT appointment_id, prescription_id, lab_test_ids FROM PAYMENT_REQUEST_TARGET WHERE payment_request_id = ?",
                paymentRequestId);
        UUID sourceId = switch (purpose) {
            case "EXAM" -> (UUID) target.get("appointment_id");
            case "PRESCRIPTION" -> (UUID) target.get("prescription_id");
            case "LAB_TEST" -> labTestId(target);
            default -> null;
        };
        if (sourceId == null) return;
        jdbc.update("""
                INSERT INTO billing_outpatient_publication_cohort(payment_request_id, account_id, purpose, source_id, acceptance_reference)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT (payment_request_id) DO NOTHING
                """, paymentRequestId, accountId, purpose, sourceId, properties.acceptanceReference());
    }

    private static UUID labTestId(java.util.Map<String, Object> target) {
        Object raw = target.get("lab_test_ids");
        if (raw == null) return null;
        try {
            UUID[] ids = (UUID[]) ((java.sql.Array) raw).getArray();
            return ids.length == 0 ? null : ids[0];
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Cannot read lab_test_ids array", exception);
        }
    }
}
