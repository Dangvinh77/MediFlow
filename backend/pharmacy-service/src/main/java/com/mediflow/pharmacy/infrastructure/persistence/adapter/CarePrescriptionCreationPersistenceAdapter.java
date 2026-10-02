package com.mediflow.pharmacy.infrastructure.persistence.adapter;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.mediflow.pharmacy.application.port.out.CarePrescriptionCreationPort;
import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;

/** Local receipt fence; no patient/episode foreign keys or cross-service writes. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class CarePrescriptionCreationPersistenceAdapter implements CarePrescriptionCreationPort {
    private final JdbcTemplate jdbc;
    public CarePrescriptionCreationPersistenceAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<UUID> claim(UUID commandId, UUID accountId, String fingerprint) {
        jdbc.update("""
                INSERT INTO care_prescription_creation(command_id, account_id, intent_fingerprint)
                VALUES (?, ?, ?) ON CONFLICT (command_id) DO NOTHING
                """, commandId, accountId, fingerprint);
        return jdbc.queryForObject("SELECT account_id, intent_fingerprint, prescription_id FROM care_prescription_creation WHERE command_id = ? FOR UPDATE",
                (rs, row) -> {
                    if (!accountId.equals(rs.getObject("account_id", UUID.class)) || !fingerprint.equals(rs.getString("intent_fingerprint"))) {
                        throw new PrescriptionRuleException("PHARMACY_CARE_CREATION_CONFLICT", "Command identity is already bound to another actor/intent");
                    }
                    return Optional.ofNullable(rs.getObject("prescription_id", UUID.class));
                }, commandId);
    }
    @Override public void complete(UUID commandId, UUID prescriptionId) {
        if (jdbc.update("UPDATE care_prescription_creation SET prescription_id = ? WHERE command_id = ? AND prescription_id IS NULL",
                prescriptionId, commandId) != 1) throw new PrescriptionRuleException("PHARMACY_CARE_CREATION_CONFLICT", "Creation receipt cannot be replaced");
    }
}
