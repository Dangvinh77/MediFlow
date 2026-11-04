package com.mediflow.organization.infrastructure.config;

import com.mediflow.organization.application.port.out.PatientAccountIdentityPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** One-time, restart-safe migration of Patient email identities through Patient's public use case. */
@Component
@Profile("!test & !acceptancefixture")
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class PatientAccountEmailBackfill implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PatientAccountIdentityPort patients;

    public PatientAccountEmailBackfill(@Qualifier("organizationDataSource") DataSource source,
            PatientAccountIdentityPort patients) {
        this.jdbc = new JdbcTemplate(source);
        this.patients = patients;
    }

    @Override
    public void run(ApplicationArguments args) {
        var accounts = jdbc.query("""
                SELECT account_id, patient_id FROM account
                WHERE role='PATIENT' AND email_migrated=false AND patient_id IS NOT NULL
                ORDER BY account_id
                """, (row, index) -> new PatientAccount(row.getObject("account_id", UUID.class),
                row.getObject("patient_id", UUID.class)));
        Map<String, List<EmailCandidate>> candidatesByEmail = new HashMap<>();
        for (PatientAccount account : accounts) {
            try {
                String email = patients.email(account.patientId());
                String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
                if (!normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) continue;
                candidatesByEmail.computeIfAbsent(normalizedEmail, ignored -> new ArrayList<>())
                        .add(new EmailCandidate(account, normalizedEmail));
            } catch (RuntimeException notEligible) {
                // Keep missing, archived or invalid profiles blocked for explicit ADMIN correction.
            }
        }
        for (List<EmailCandidate> sameAddress : candidatesByEmail.values()) {
            // A contact address is not a unique login identity until every duplicate patient
            // has been corrected by an administrator. Do not choose a winner by UUID/order.
            if (sameAddress.size() != 1) continue;
            EmailCandidate candidate = sameAddress.getFirst();
            PatientAccount account = candidate.account();
            try {
                int ownerCount = jdbc.queryForObject("""
                        SELECT count(*) FROM account
                        WHERE role='PATIENT' AND patient_id=? AND email_migrated=false
                        """, Integer.class, account.patientId());
                if (ownerCount != 1) continue;
                jdbc.update("""
                        UPDATE account SET username=?, email_migrated=true
                        WHERE account_id=? AND email_migrated=false
                          AND NOT EXISTS (SELECT 1 FROM account other
                            WHERE other.account_id<>? AND lower(btrim(other.username))=?)
                        """, candidate.email(), account.accountId(), account.accountId(), candidate.email());
            } catch (RuntimeException migrationConflict) {
                // A competing startup or existing login identity can win the normalized unique key;
                // leave this legacy row blocked and let an ADMIN resolve it rather than aborting startup.
            }
        }
    }

    private record PatientAccount(UUID accountId, UUID patientId) { }
    private record EmailCandidate(PatientAccount account, String email) { }
}
