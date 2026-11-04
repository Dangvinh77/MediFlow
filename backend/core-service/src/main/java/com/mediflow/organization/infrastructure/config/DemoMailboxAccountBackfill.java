package com.mediflow.organization.infrastructure.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.organization.domain.model.Role;
import com.mediflow.organization.application.port.out.PatientAccountIdentityPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;

/** Register only existing, receipt-backed synthetic seed accounts in the local demo mailbox. */
@Component
@Profile("!test & !acceptancefixture")
@ConditionalOnProperty(name = "mediflow.demo-mailbox.enabled", havingValue = "true")
@Order(Ordered.LOWEST_PRECEDENCE)
public class DemoMailboxAccountBackfill implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PatientAccountIdentityPort patients;

    public DemoMailboxAccountBackfill(@Qualifier("organizationDataSource") DataSource source, ObjectMapper mapper,
            PatientAccountIdentityPort patients) {
        this.jdbc = new JdbcTemplate(source);
        this.mapper = mapper;
        this.patients = patients;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (var input = new ClassPathResource("demo-seed.json").getInputStream()) {
            registerSeedAccounts(mapper.readTree(input));
        } catch (IOException invalidFixture) {
            throw new IllegalStateException("Could not read trusted local demo mailbox fixture", invalidFixture);
        }
    }

    void registerSeedAccounts(JsonNode data) {
        for (JsonNode account : data.required("accounts")) {
            String seedKey = text(account, "key");
            if (seedKey == null || !seedKey.startsWith("account-demo.")) continue;
            Role role = Role.valueOf(text(account, "role"));
            String ownerKey = text(account, role == Role.PATIENT ? "patientKey" : "staffKey");
            if (ownerKey == null) continue;
            String email = OrganizationDemoSeeder.seedOwnerEmail(data, account, role);
            if (!OrganizationDemoSeeder.isSyntheticSeedEmail(email)) continue;

            if (role == Role.PATIENT) {
                // Patient owns its profile receipts. Use its identity port rather than
                // joining a Patient receipt in Organization's separate store.
                var candidates = jdbc.query("""
                        SELECT a.account_id,a.patient_id FROM account a
                        JOIN local_demo_seed_receipt r ON r.entity_id=a.account_id AND r.seed_key=?
                        WHERE a.role='PATIENT' AND a.username=? AND a.email_migrated=true
                        """, (rs, index) -> new java.util.UUID[]{rs.getObject(1, java.util.UUID.class),
                        rs.getObject(2, java.util.UUID.class)}, seedKey, email);
                for (var candidate : candidates) {
                    try {
                        if (!email.equals(patients.email(candidate[1]))) continue;
                    } catch (RuntimeException unavailableProfile) { continue; }
                    jdbc.update("""
                            INSERT INTO local_demo_mailbox_account(account_id,seed_key,synthetic_email)
                            SELECT ?,?,? WHERE NOT EXISTS (
                              SELECT 1 FROM local_demo_mailbox_account WHERE account_id=? OR synthetic_email=?)
                            AND NOT EXISTS (SELECT 1 FROM account WHERE account_id<>? AND lower(trim(username))=?)
                            """, candidate[0], seedKey, email, candidate[0], email, candidate[0], email);
                }
                continue;
            }

            // The receipt proves this account and profile were created by the trusted seed.
            // The exact current email must still match the fixture, so an admin-edited real
            // address can never inherit local mailbox access merely because it looks synthetic.
            String ownerColumn = role == Role.PATIENT ? "patient_id" : "staff_id";
            jdbc.update(("""
                    INSERT INTO local_demo_mailbox_account(account_id,seed_key,synthetic_email)
                    SELECT a.account_id, ?, ?
                    FROM account a
                    JOIN local_demo_seed_receipt account_receipt
                      ON account_receipt.entity_id=a.account_id AND account_receipt.seed_key=?
                    JOIN local_demo_seed_receipt owner_receipt
                      ON owner_receipt.entity_id=a.%s AND owner_receipt.seed_key=?
                    WHERE a.role=? AND a.username=? AND a.email_migrated=true
                      AND NOT EXISTS (SELECT 1 FROM account other
                        WHERE other.account_id<>a.account_id AND lower(trim(other.username))=lower(trim(?)))
                      AND NOT EXISTS (SELECT 1 FROM local_demo_mailbox_account mapped
                        WHERE mapped.account_id=a.account_id OR mapped.synthetic_email=?)
                    """).formatted(ownerColumn), seedKey, email, seedKey, ownerKey,
                    role.name(), email, email, email);
        }
    }

    private static String text(JsonNode row, String field) {
        return row.path(field).isMissingNode() || row.path(field).isNull() ? null : row.path(field).asText();
    }
}
