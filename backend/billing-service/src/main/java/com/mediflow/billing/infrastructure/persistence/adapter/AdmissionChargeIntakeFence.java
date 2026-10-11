package com.mediflow.billing.infrastructure.persistence.adapter;

import com.mediflow.billing.domain.exception.BillingRuleException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Local shared episode lock before account creation; no cross-service/distributed lock is claimed. */
final class AdmissionChargeIntakeFence {
    private AdmissionChargeIntakeFence() { }

    static void lock(JdbcTemplate jdbc, UUID admission) {
        jdbc.execute("SET LOCAL lock_timeout='3s'");
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "billing-admission-close:" + admission);
    }

    static void requireOpen(JdbcTemplate jdbc, String episodeType, UUID episode, UUID patient) {
        if (!"ADMISSION".equals(episodeType)) return;
        lock(jdbc, episode);
        var proof = jdbc.queryForList("SELECT patient_id FROM admission_charge_close WHERE admission_id=?", episode);
        if (proof.isEmpty()) return;
        // A Pharmacy/Surgery account request is not authority to replace Inpatient's patient tuple.
        // Even an apparent early mismatch must remain denied until an explicit owner correction.
        if (!patient.equals(proof.getFirst().get("patient_id"))) throw new BillingRuleException(
                "BILLING_MEDICAL_DISCHARGE_CONFLICT", "Admission patient differs from medical-discharge proof");
        throw new BillingRuleException("BILLING_ACCOUNT_CHARGE_CLOSED", "Admission charge intake is medically closed");
    }
}
