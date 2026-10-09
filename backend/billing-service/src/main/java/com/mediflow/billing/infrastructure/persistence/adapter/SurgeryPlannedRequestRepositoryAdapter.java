package com.mediflow.billing.infrastructure.persistence.adapter;

import com.mediflow.billing.application.dto.command.SurgeryChargeCommand;
import com.mediflow.billing.application.port.out.SurgeryPlannedRequestRepositoryPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.Charge;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class SurgeryPlannedRequestRepositoryAdapter implements SurgeryPlannedRequestRepositoryPort {
    private final JdbcTemplate jdbc;
    public SurgeryPlannedRequestRepositoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void claimDelivery(SurgeryChargeCommand command) {
        jdbc.execute("SET LOCAL lock_timeout='3s'");
        jdbc.update("INSERT INTO surgery_charge_delivery(event_id,surgery_case_id,payload_fingerprint) VALUES (?,?,?) ON CONFLICT(event_id) DO NOTHING",
                command.eventId(), command.surgeryCaseId(), command.deliveryFingerprint());
        var previous = jdbc.queryForObject("SELECT payload_fingerprint FROM surgery_charge_delivery WHERE event_id=?", String.class, command.eventId());
        same(command.deliveryFingerprint(), previous);
    }
    @Override public Optional<UUID> lockRecordedSource(SurgeryChargeCommand command) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "surgery-charge:" + command.surgeryCaseId());
        var recorded = jdbc.queryForList("SELECT * FROM surgery_charge_source WHERE surgery_case_id=?", command.surgeryCaseId());
        if (recorded.isEmpty()) return Optional.empty();
        var source = recorded.getFirst();
        same(command.surgeryRequestId(), source.get("surgery_request_id")); same(command.sourceFingerprint(), source.get("source_fingerprint"));
        return Optional.of((UUID) source.get("payment_request_id"));
    }
    @Override public UUID openAndLockExactAccount(SurgeryChargeCommand command) {
        jdbc.update("""
                INSERT INTO BILLING_ACCOUNT(account_id,patient_id,department_id,care_episode_type,care_episode_id,currency,opened_at)
                VALUES (?,?,?,?,?,'VND',?) ON CONFLICT(care_episode_type,care_episode_id) DO NOTHING
                """, UUID.randomUUID(), command.patientId(), command.departmentId(), command.careEpisodeType(), command.careEpisodeId(), Timestamp.from(command.requestedAt()));
        var account = jdbc.queryForMap("SELECT * FROM BILLING_ACCOUNT WHERE care_episode_type=? AND care_episode_id=? FOR UPDATE",
                command.careEpisodeType(), command.careEpisodeId());
        // One episode may incur charges from multiple departments. Preserve account attribution;
        // every new charge retains the explicit generating department from its producer.
        same(command.patientId(), account.get("patient_id"));
        same("VND", account.get("currency").toString().trim());
        require("OPEN".equals(account.get("status")), "BILLING_ACCOUNT_CHARGE_CLOSED");
        return (UUID) account.get("account_id");
    }
    @Override public IssuedRequest saveChargesAndRequest(SurgeryChargeCommand command, UUID accountId, List<Charge> charges) {
        UUID request = UUID.randomUUID(), invoice = UUID.randomUUID();
        BigDecimal total = charges.stream().map(Charge::getGrossAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        jdbc.update("""
                INSERT INTO PAYMENT_REQUEST(payment_request_id,invoice_id,account_id,purpose,requested_amount,currency,created_by)
                VALUES (?,?,?,'SURGERY',?,'VND',?)
                """, request, invoice, accountId, total, command.requestedBy());
        jdbc.update("INSERT INTO PAYMENT_REQUEST_TARGET(payment_request_id,admission_id,surgery_case_id) VALUES (?,?,?)",
                request, command.admissionId(), command.surgeryCaseId());
        jdbc.update("""
                INSERT INTO surgery_charge_source(surgery_case_id,surgery_request_id,source_fingerprint,payment_request_id,record_id,requested_at,requested_at_iso)
                VALUES (?,?,?,?,?,?,?)
                """, command.surgeryCaseId(), command.surgeryRequestId(), command.sourceFingerprint(), request, command.recordId(), Timestamp.from(command.requestedAt()), command.requestedAt().toString());
        var ids = new HashMap<String, UUID>();
        for (var charge : charges) {
            UUID id = UUID.randomUUID(); ids.put(charge.getPriceCode(), id);
            jdbc.update("""
                    INSERT INTO CHARGE(charge_id,account_id,patient_id,department_id,source_type,source_id,price_code,
                        description,quantity,unit_amount,gross_amount,incurred_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                    """, id, accountId, command.patientId(), command.departmentId(), "SURGERY", command.surgeryCaseId(),
                    charge.getPriceCode(), charge.getDescription(), charge.getQuantity(), charge.getUnitAmount(), charge.getGrossAmount(), Timestamp.from(charge.getIncurredAt()));
            jdbc.update("INSERT INTO PAYMENT_REQUEST_CHARGE(payment_request_id,charge_id,requested_amount) VALUES (?,?,?)",
                    request, id, charge.getGrossAmount());
        }
        for (var item : command.plannedItems()) jdbc.update("""
                INSERT INTO surgery_charge_item(surgery_case_id,item_code,price_code,quantity,charge_id) VALUES (?,?,?,?,?)
                """, command.surgeryCaseId(), item.itemCode(), item.priceCode(), item.quantity(), ids.get(item.priceCode()));
        return new IssuedRequest(request, invoice, total);
    }
    private static void same(Object expected, Object actual) { require(Objects.equals(expected, actual), "BILLING_SURGERY_SOURCE_CONFLICT"); }
    private static void require(boolean valid, String code) { if (!valid) throw new BillingRuleException(code, code); }
}
