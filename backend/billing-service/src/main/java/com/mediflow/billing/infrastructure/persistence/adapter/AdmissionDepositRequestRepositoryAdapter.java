package com.mediflow.billing.infrastructure.persistence.adapter;

import com.mediflow.billing.application.dto.command.AdmissionDepositRequestCommand;
import com.mediflow.billing.application.port.out.AdmissionDepositRequestRepositoryPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
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
public class AdmissionDepositRequestRepositoryAdapter implements AdmissionDepositRequestRepositoryPort {
    private final JdbcTemplate jdbc;
    public AdmissionDepositRequestRepositoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void claimDelivery(AdmissionDepositRequestCommand command) {
        jdbc.execute("SET LOCAL lock_timeout='3s'");
        jdbc.update("INSERT INTO admission_deposit_delivery(event_id,admission_id,payload_fingerprint) VALUES (?,?,?) ON CONFLICT(event_id) DO NOTHING",
                command.eventId(), command.admissionId(), command.deliveryFingerprint());
        var previous = jdbc.queryForObject("SELECT payload_fingerprint FROM admission_deposit_delivery WHERE event_id=?", String.class, command.eventId());
        same(command.deliveryFingerprint(), previous);
    }
    @Override public Optional<UUID> lockRecordedSource(AdmissionDepositRequestCommand command) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "admission-deposit:" + command.admissionId());
        var recorded = jdbc.queryForList("SELECT * FROM admission_deposit_source WHERE admission_id=?", command.admissionId());
        if (recorded.isEmpty()) return Optional.empty();
        var source = recorded.getFirst();
        same(command.sourceFingerprint(), source.get("source_fingerprint"));
        return Optional.of((UUID) source.get("payment_request_id"));
    }
    @Override public UUID openAndLockExactAccount(AdmissionDepositRequestCommand command) {
        jdbc.update("""
                INSERT INTO BILLING_ACCOUNT(account_id,patient_id,department_id,care_episode_type,care_episode_id,currency,opened_at)
                VALUES (?,?,?,?,?,'VND',?) ON CONFLICT(care_episode_type,care_episode_id) DO NOTHING
                """, UUID.randomUUID(), command.patientId(), command.departmentId(), command.careEpisodeType(), command.careEpisodeId(), Timestamp.from(command.occurredAt()));
        var account = jdbc.queryForMap("SELECT * FROM BILLING_ACCOUNT WHERE care_episode_type=? AND care_episode_id=? FOR UPDATE",
                command.careEpisodeType(), command.careEpisodeId());
        same(command.patientId(), account.get("patient_id"));
        same("VND", account.get("currency").toString().trim());
        require("OPEN".equals(account.get("status")), "BILLING_ACCOUNT_CHARGE_CLOSED");
        return (UUID) account.get("account_id");
    }
    @Override public UUID saveRequest(AdmissionDepositRequestCommand command, UUID accountId) {
        UUID request = UUID.randomUUID(), invoice = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO PAYMENT_REQUEST(payment_request_id,invoice_id,account_id,purpose,requested_amount,currency)
                VALUES (?,?,?,'ADMISSION_DEPOSIT',?,'VND')
                """, request, invoice, accountId, command.suggestedAmount());
        jdbc.update("INSERT INTO PAYMENT_REQUEST_TARGET(payment_request_id,admission_id) VALUES (?,?)",
                request, command.admissionId());
        jdbc.update("""
                INSERT INTO admission_deposit_source(admission_id,source_fingerprint,payment_request_id,requested_at)
                VALUES (?,?,?,?)
                """, command.admissionId(), command.sourceFingerprint(), request, Timestamp.from(command.occurredAt()));
        return request;
    }
    private static void same(Object expected, Object actual) { require(Objects.equals(expected, actual), "BILLING_ADMISSION_DEPOSIT_SOURCE_CONFLICT"); }
    private static void require(boolean valid, String code) { if (!valid) throw new BillingRuleException(code, code); }
}
