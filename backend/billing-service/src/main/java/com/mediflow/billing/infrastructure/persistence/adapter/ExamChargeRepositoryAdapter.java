package com.mediflow.billing.infrastructure.persistence.adapter;

import com.mediflow.billing.application.dto.command.ExamChargeCommand;
import com.mediflow.billing.application.port.out.ExamChargeRepositoryPort;
import com.mediflow.billing.application.port.out.OutpatientPublicationCohortPort;
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
public class ExamChargeRepositoryAdapter implements ExamChargeRepositoryPort {
    private final JdbcTemplate jdbc;
    private final OutpatientPublicationCohortPort cohort;
    public ExamChargeRepositoryAdapter(JdbcTemplate jdbc, OutpatientPublicationCohortPort cohort) {
        this.jdbc = jdbc;
        this.cohort = cohort;
    }

    @Override public void claimDelivery(ExamChargeCommand command) {
        jdbc.execute("SET LOCAL lock_timeout='3s'");
        jdbc.update("INSERT INTO exam_charge_delivery(event_id,appointment_id,payload_fingerprint) VALUES (?,?,?) ON CONFLICT(event_id) DO NOTHING",
                command.eventId(), command.appointmentId(), command.deliveryFingerprint());
        var previous = jdbc.queryForObject("SELECT payload_fingerprint FROM exam_charge_delivery WHERE event_id=?", String.class, command.eventId());
        same(command.deliveryFingerprint(), previous);
    }
    @Override public Optional<UUID> lockRecordedSource(ExamChargeCommand command) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "exam-charge:" + command.appointmentId());
        var recorded = jdbc.queryForList("SELECT * FROM exam_charge_source WHERE appointment_id=?", command.appointmentId());
        if (recorded.isEmpty()) return Optional.empty();
        var source = recorded.getFirst();
        same(command.sourceFingerprint(), source.get("source_fingerprint"));
        return Optional.of((UUID) source.get("payment_request_id"));
    }
    @Override public UUID openAndLockExactAccount(ExamChargeCommand command) {
        jdbc.update("""
                INSERT INTO BILLING_ACCOUNT(account_id,patient_id,department_id,care_episode_type,care_episode_id,currency,opened_at)
                VALUES (?,?,?,?,?,'VND',?) ON CONFLICT(care_episode_type,care_episode_id) DO NOTHING
                """, UUID.randomUUID(), command.patientId(), command.departmentId(), command.careEpisodeType(), command.careEpisodeId(), Timestamp.from(command.changedAt()));
        var account = jdbc.queryForMap("SELECT * FROM BILLING_ACCOUNT WHERE care_episode_type=? AND care_episode_id=? FOR UPDATE",
                command.careEpisodeType(), command.careEpisodeId());
        same(command.patientId(), account.get("patient_id"));
        same("VND", account.get("currency").toString().trim());
        require("OPEN".equals(account.get("status")), "BILLING_ACCOUNT_CHARGE_CLOSED");
        return (UUID) account.get("account_id");
    }
    @Override public IssuedRequest saveChargeAndRequest(ExamChargeCommand command, UUID accountId, Charge charge) {
        UUID request = UUID.randomUUID(), invoice = UUID.randomUUID(), chargeId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO PAYMENT_REQUEST(payment_request_id,invoice_id,account_id,purpose,requested_amount,currency)
                VALUES (?,?,?,'EXAM',?,'VND')
                """, request, invoice, accountId, charge.getGrossAmount());
        jdbc.update("INSERT INTO PAYMENT_REQUEST_TARGET(payment_request_id,appointment_id) VALUES (?,?)",
                request, command.appointmentId());
        jdbc.update("""
                INSERT INTO exam_charge_source(appointment_id,source_fingerprint,payment_request_id,requested_at)
                VALUES (?,?,?,?)
                """, command.appointmentId(), command.sourceFingerprint(), request, Timestamp.from(command.changedAt()));
        jdbc.update("""
                INSERT INTO CHARGE(charge_id,account_id,patient_id,department_id,source_type,source_id,price_code,
                    description,quantity,unit_amount,gross_amount,incurred_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, chargeId, accountId, command.patientId(), command.departmentId(), "EXAM", command.appointmentId(),
                charge.getPriceCode(), charge.getDescription(), charge.getQuantity(), charge.getUnitAmount(),
                charge.getGrossAmount(), Timestamp.from(charge.getIncurredAt()));
        jdbc.update("INSERT INTO PAYMENT_REQUEST_CHARGE(payment_request_id,charge_id,requested_amount) VALUES (?,?,?)",
                request, chargeId, charge.getGrossAmount());
        cohort.enrollIfEligible(request);
        return new IssuedRequest(request, invoice, charge.getGrossAmount());
    }
    private static void same(Object expected, Object actual) { require(Objects.equals(expected, actual), "BILLING_EXAM_CHARGE_SOURCE_CONFLICT"); }
    private static void require(boolean valid, String code) { if (!valid) throw new BillingRuleException(code, code); }
}
