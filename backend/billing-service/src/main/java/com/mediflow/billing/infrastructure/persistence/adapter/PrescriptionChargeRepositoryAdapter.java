package com.mediflow.billing.infrastructure.persistence.adapter;

import com.mediflow.billing.application.dto.command.PrescriptionChargeCommand;
import com.mediflow.billing.application.port.out.OutpatientPublicationCohortPort;
import com.mediflow.billing.application.port.out.PrescriptionChargeRepositoryPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.Charge;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class PrescriptionChargeRepositoryAdapter implements PrescriptionChargeRepositoryPort {
    private final JdbcTemplate jdbc;
    private final OutpatientPublicationCohortPort cohort;
    public PrescriptionChargeRepositoryAdapter(JdbcTemplate jdbc, OutpatientPublicationCohortPort cohort) {
        this.jdbc = jdbc;
        this.cohort = cohort;
    }

    @Override public void claimDelivery(PrescriptionChargeCommand command) {
        jdbc.execute("SET LOCAL lock_timeout='3s'");
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "prescription-delivery:" + command.eventId());
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM prescription_terminal_delivery WHERE event_id=?)", Boolean.class, command.eventId())))
            throw new BillingRuleException("BILLING_PRESCRIPTION_DELIVERY_CONFLICT", "BILLING_PRESCRIPTION_DELIVERY_CONFLICT");
        jdbc.update("INSERT INTO prescription_charge_delivery(event_id,prescription_id,payload_fingerprint) VALUES (?,?,?) ON CONFLICT(event_id) DO NOTHING",
                command.eventId(), command.prescriptionId(), command.deliveryFingerprint());
        var previous = jdbc.queryForMap("SELECT prescription_id,payload_fingerprint FROM prescription_charge_delivery WHERE event_id=?", command.eventId());
        same(command.prescriptionId(), previous.get("prescription_id"));
        same(command.deliveryFingerprint(), previous.get("payload_fingerprint"));
    }
    @Override public Optional<IssuedCharge> lockRecordedSource(PrescriptionChargeCommand command) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "prescription-charge:" + command.prescriptionId());
        var sources = jdbc.queryForList("SELECT * FROM prescription_charge_source WHERE prescription_id=?", command.prescriptionId());
        if (sources.isEmpty()) return Optional.empty();
        var source = sources.getFirst(); same(command.sourceFingerprint(), source.get("source_fingerprint"));
        // Only a revalidated original creation, never terminal bytes, may upgrade an old proof.
        jdbc.update("UPDATE prescription_charge_source SET snapshot_fingerprint=? WHERE prescription_id=? AND snapshot_fingerprint IS NULL",
                command.snapshotFingerprint(), command.prescriptionId());
        return Optional.of(new IssuedCharge((UUID) source.get("charge_id"), (UUID) source.get("account_id"),
                (UUID) source.get("payment_request_id"), (UUID) source.get("invoice_id")));
    }
    @Override public UUID openAndLockExactAccount(PrescriptionChargeCommand command) {
        AdmissionChargeIntakeFence.requireOpen(jdbc, command.careEpisodeType(), command.careEpisodeId(), command.patientId());
        jdbc.update("""
                INSERT INTO BILLING_ACCOUNT(account_id,patient_id,department_id,care_episode_type,care_episode_id,currency,opened_at)
                VALUES (?,?,?,?,?,'VND',?) ON CONFLICT(care_episode_type,care_episode_id) DO NOTHING
                """, UUID.randomUUID(), command.patientId(), command.departmentId(), command.careEpisodeType(), command.careEpisodeId(), Timestamp.from(command.createdAt()));
        var account = jdbc.queryForMap("SELECT * FROM BILLING_ACCOUNT WHERE care_episode_type=? AND care_episode_id=? FOR UPDATE",
                command.careEpisodeType(), command.careEpisodeId());
        same(command.patientId(), account.get("patient_id")); same("VND", account.get("currency").toString().trim());
        if (!"OPEN".equals(account.get("status"))) throw new BillingRuleException("BILLING_ACCOUNT_CHARGE_CLOSED", "BILLING_ACCOUNT_CHARGE_CLOSED");
        return (UUID) account.get("account_id");
    }
    @Override public IssuedCharge saveChargeAndRequest(PrescriptionChargeCommand command, UUID account, Charge charge, Instant issuedAt) {
        UUID chargeId = UUID.randomUUID(), request = null, invoice = null;
        jdbc.update("""
                INSERT INTO CHARGE(charge_id,account_id,patient_id,department_id,source_type,source_id,price_code,
                    description,quantity,unit_amount,gross_amount,incurred_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, chargeId, account, command.patientId(), command.departmentId(), charge.getSourceType(), command.prescriptionId(),
                command.priceCode(), charge.getDescription(), charge.getQuantity(), charge.getUnitAmount(), charge.getGrossAmount(), Timestamp.from(command.createdAt()));
        if ("OUTPATIENT".equals(command.careContext())) {
            request = UUID.randomUUID(); invoice = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO PAYMENT_REQUEST(payment_request_id,invoice_id,account_id,purpose,requested_amount,currency,created_at)
                    VALUES (?,?,?,'PRESCRIPTION',?,'VND',?)
                    """, request, invoice, account, command.totalAmount(), Timestamp.from(issuedAt));
            // recordId is context evidence, never an EXAM permission bundled into an Rx grant.
            jdbc.update("INSERT INTO PAYMENT_REQUEST_TARGET(payment_request_id,prescription_id) VALUES (?,?)", request, command.prescriptionId());
            jdbc.update("INSERT INTO PAYMENT_REQUEST_CHARGE(payment_request_id,charge_id,requested_amount) VALUES (?,?,?)", request, chargeId, command.totalAmount());
            cohort.enrollIfEligible(request);
        }
        jdbc.update("""
                INSERT INTO prescription_charge_source(prescription_id,source_fingerprint,account_id,charge_id,payment_request_id,invoice_id,
                    care_context,record_id,created_at_iso,snapshot_fingerprint) VALUES (?,?,?,?,?,?,?,?,?,?)
                """, command.prescriptionId(), command.sourceFingerprint(), account, chargeId, request, invoice, command.careContext(), command.recordId(), command.createdAt().toString(), command.snapshotFingerprint());
        for (var item : command.items()) jdbc.update("INSERT INTO prescription_charge_item(prescription_id,drug_id,quantity,unit_price) VALUES (?,?,?,?)",
                command.prescriptionId(), item.drugId(), item.quantity(), item.unitPrice());
        return new IssuedCharge(chargeId, account, request, invoice);
    }
    private static void same(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new BillingRuleException("BILLING_PRESCRIPTION_SOURCE_CONFLICT", "BILLING_PRESCRIPTION_SOURCE_CONFLICT");
    }
}
