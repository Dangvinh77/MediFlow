package com.mediflow.billing.infrastructure.persistence.adapter;

import com.mediflow.billing.application.dto.command.LabTestChargeCommand;
import com.mediflow.billing.application.port.out.LabTestChargeRepositoryPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.Charge;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class LabTestChargeRepositoryAdapter implements LabTestChargeRepositoryPort {
    private final JdbcTemplate jdbc;
    public LabTestChargeRepositoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void claimDelivery(LabTestChargeCommand command) {
        jdbc.execute("SET LOCAL lock_timeout='3s'");
        jdbc.update("INSERT INTO lab_test_charge_delivery(event_id,lab_id,payload_fingerprint) VALUES (?,?,?) ON CONFLICT(event_id) DO NOTHING",
                command.eventId(), command.labId(), command.deliveryFingerprint());
        var previous = jdbc.queryForObject("SELECT payload_fingerprint FROM lab_test_charge_delivery WHERE event_id=?", String.class, command.eventId());
        same(command.deliveryFingerprint(), previous);
    }
    @Override public Optional<UUID> lockRecordedSource(LabTestChargeCommand command) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "lab-test-charge:" + command.labId());
        var recorded = jdbc.queryForList("SELECT * FROM lab_test_charge_source WHERE lab_id=?", command.labId());
        if (recorded.isEmpty()) return Optional.empty();
        var source = recorded.getFirst();
        same(command.sourceOrderId(), source.get("source_order_id")); same(command.sourceFingerprint(), source.get("source_fingerprint"));
        return Optional.of((UUID) source.get("payment_request_id"));
    }
    @Override public UUID openAndLockExactAccount(LabTestChargeCommand command) {
        jdbc.update("""
                INSERT INTO BILLING_ACCOUNT(account_id,patient_id,department_id,care_episode_type,care_episode_id,currency,opened_at)
                VALUES (?,?,?,?,?,'VND',?) ON CONFLICT(care_episode_type,care_episode_id) DO NOTHING
                """, UUID.randomUUID(), command.patientId(), command.departmentId(), command.careEpisodeType(), command.careEpisodeId(), Timestamp.from(command.requestedAt()));
        var account = jdbc.queryForMap("SELECT * FROM BILLING_ACCOUNT WHERE care_episode_type=? AND care_episode_id=? FOR UPDATE",
                command.careEpisodeType(), command.careEpisodeId());
        same(command.patientId(), account.get("patient_id"));
        same("VND", account.get("currency").toString().trim());
        require("OPEN".equals(account.get("status")), "BILLING_ACCOUNT_CHARGE_CLOSED");
        return (UUID) account.get("account_id");
    }
    @Override public IssuedRequest saveChargeAndRequest(LabTestChargeCommand command, UUID accountId, Charge charge) {
        UUID request = UUID.randomUUID(), invoice = UUID.randomUUID(), chargeId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO PAYMENT_REQUEST(payment_request_id,invoice_id,account_id,purpose,requested_amount,currency)
                VALUES (?,?,?,'LAB_TEST',?,'VND')
                """, request, invoice, accountId, charge.getGrossAmount());
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO PAYMENT_REQUEST_TARGET(payment_request_id,record_id,lab_test_ids) VALUES (?,?,?)");
            statement.setObject(1, request);
            statement.setObject(2, command.recordId());
            statement.setArray(3, connection.createArrayOf("uuid", new UUID[]{command.labId()}));
            return statement;
        });
        jdbc.update("""
                INSERT INTO lab_test_charge_source(lab_id,source_order_id,source_fingerprint,payment_request_id,requested_at)
                VALUES (?,?,?,?,?)
                """, command.labId(), command.sourceOrderId(), command.sourceFingerprint(), request, Timestamp.from(command.requestedAt()));
        jdbc.update("""
                INSERT INTO CHARGE(charge_id,account_id,patient_id,department_id,source_type,source_id,price_code,
                    description,quantity,unit_amount,gross_amount,incurred_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, chargeId, accountId, command.patientId(), command.departmentId(), "LAB_TEST", command.labId(),
                charge.getPriceCode(), charge.getDescription(), charge.getQuantity(), charge.getUnitAmount(),
                charge.getGrossAmount(), Timestamp.from(charge.getIncurredAt()));
        jdbc.update("INSERT INTO PAYMENT_REQUEST_CHARGE(payment_request_id,charge_id,requested_amount) VALUES (?,?,?)",
                request, chargeId, charge.getGrossAmount());
        return new IssuedRequest(request, invoice, charge.getGrossAmount());
    }
    private static void same(Object expected, Object actual) { require(Objects.equals(expected, actual), "BILLING_LAB_TEST_SOURCE_CONFLICT"); }
    private static void require(boolean valid, String code) { if (!valid) throw new BillingRuleException(code, code); }
}
