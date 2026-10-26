package com.mediflow.notification.infrastructure.persistence.adapter;

import com.mediflow.notification.application.dto.command.RegistrationNoticeCommand;
import com.mediflow.notification.application.port.out.RegistrationNoticeProjectionPort;
import com.mediflow.notification.domain.exception.NotificationEventConflictException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Repository
@Transactional(transactionManager = "notificationTransactionManager", propagation = Propagation.MANDATORY)
public class RegistrationNoticeProjectionAdapter implements RegistrationNoticeProjectionPort {
    private static final Set<String> TERMINAL = Set.of("CANCELLED", "EXPIRED", "COMPLETED");
    private final JdbcTemplate jdbc;
    public RegistrationNoticeProjectionAdapter(@Qualifier("notificationJdbcTemplate") JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public boolean advance(RegistrationNoticeCommand command) {
        jdbc.update("INSERT INTO outpatient_registration_notice_cursor(registration_id,patient_id) VALUES (?,?) ON CONFLICT DO NOTHING",
                command.registrationId(), command.patientId());
        var cursor = jdbc.queryForMap("SELECT patient_id,revision,status FROM outpatient_registration_notice_cursor WHERE registration_id=? FOR UPDATE",
                command.registrationId());
        if (!command.patientId().equals(cursor.get("patient_id"))) throw new NotificationEventConflictException();
        var known = jdbc.queryForList("SELECT snapshot_hash FROM outpatient_registration_notice_revision WHERE registration_id=? AND revision=?",
                String.class, command.registrationId(), command.revision());
        if (!known.isEmpty()) {
            if (!command.snapshotHash().equals(known.getFirst())) throw new NotificationEventConflictException();
            return false;
        }
        long current = ((Number) cursor.get("revision")).longValue();
        boolean stale = command.revision() <= current;
        boolean terminal = cursor.get("status") != null && TERMINAL.contains(cursor.get("status").toString());
        // Terminal facts can establish a tombstone before create. Nonterminal gaps must retry.
        if (!stale && !terminal && command.revision() != current + 1 && !TERMINAL.contains(command.status()))
            throw new ConcurrencyFailureException("Registration notice revision gap; await missing source facts");
        jdbc.update("INSERT INTO outpatient_registration_notice_revision(registration_id,revision,snapshot_hash) VALUES (?,?,?)",
                command.registrationId(), command.revision(), command.snapshotHash());
        if (stale || terminal) return false;
        jdbc.update("UPDATE outpatient_registration_notice_cursor SET revision=?,status=? WHERE registration_id=?",
                command.revision(), command.status(), command.registrationId());
        return !command.status().equals(cursor.get("status"));
    }
}
