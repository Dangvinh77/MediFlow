package com.mediflow.organization.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;
import com.mediflow.organization.application.port.out.PatientAccountIdentityPort;
import static org.mockito.Mockito.*;

import static org.assertj.core.api.Assertions.assertThat;

class DemoMailboxAccountBackfillTest {
    private JdbcTemplate jdbc;
    private DemoMailboxAccountBackfill backfill;
    private PatientAccountIdentityPort patients;
    private final UUID accountId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("create table account(account_id uuid primary key, staff_id uuid, patient_id uuid, role varchar(30), username varchar(254), email_migrated boolean)");
        jdbc.execute("create table local_demo_seed_receipt(seed_key varchar(100) primary key, entity_id uuid)");
        jdbc.execute("create table local_demo_mailbox_account(account_id uuid primary key, seed_key varchar(100) unique, synthetic_email varchar(254) unique)");
        jdbc.update("insert into account values (?, ?, null, 'ADMIN', 'demo.staff01@example.test', true)", accountId, staffId);
        jdbc.update("insert into local_demo_seed_receipt values ('account-demo.admin', ?)", accountId);
        jdbc.update("insert into local_demo_seed_receipt values ('staff-01', ?)", staffId);
        patients = mock(PatientAccountIdentityPort.class);
        backfill = new DemoMailboxAccountBackfill(source, new ObjectMapper(), patients);
    }

    @Test
    void registersPatientThroughItsIdentityPortWithoutOrganizationOwnerReceipt() throws Exception {
        UUID patientId = UUID.randomUUID();
        jdbc.update("update account set role='PATIENT',staff_id=null,patient_id=?,username='demo.patient01@example.test'", patientId);
        jdbc.update("update local_demo_seed_receipt set seed_key='account-demo.patient' where seed_key='account-demo.admin'");
        when(patients.email(patientId)).thenReturn("demo.patient01@example.test");
        var fixture = new ObjectMapper().readTree("""
                {"staff":[],"patients":[{"key":"patient-01","email":"demo.patient01@example.test"}],
                 "accounts":[{"key":"account-demo.patient","role":"PATIENT","patientKey":"patient-01"}]}
                """);
        backfill.registerSeedAccounts(fixture);
        backfill.registerSeedAccounts(fixture);
        assertThat(jdbc.queryForObject("select count(*) from local_demo_mailbox_account", Integer.class)).isEqualTo(1);
        jdbc.update("delete from local_demo_mailbox_account");
        when(patients.email(patientId)).thenReturn("real@example.com");
        backfill.registerSeedAccounts(fixture);
        assertThat(jdbc.queryForObject("select count(*) from local_demo_mailbox_account", Integer.class)).isZero();
    }

    @Test
    void registersVerifiedExistingSnapshotReceiptWithoutRunningAccountSeeder() throws Exception {
        backfill.registerSeedAccounts(new ObjectMapper().readTree(seedData("demo.staff01@example.test")));

        assertThat(jdbc.queryForObject("select count(*) from local_demo_mailbox_account", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select seed_key from local_demo_mailbox_account", String.class))
                .isEqualTo("account-demo.admin");
        assertThat(jdbc.queryForObject("select synthetic_email from local_demo_mailbox_account", String.class))
                .isEqualTo("demo.staff01@example.test");
    }

    @Test
    void doesNotRegisterForgedReceiptOwnerOrRealAddress() throws Exception {
        jdbc.update("update local_demo_seed_receipt set entity_id=? where seed_key='staff-01'", UUID.randomUUID());
        backfill.registerSeedAccounts(new ObjectMapper().readTree(seedData("demo.staff01@example.test")));
        assertThat(jdbc.queryForObject("select count(*) from local_demo_mailbox_account", Integer.class)).isZero();

        jdbc.update("update local_demo_seed_receipt set entity_id=? where seed_key='staff-01'", staffId);
        backfill.registerSeedAccounts(new ObjectMapper().readTree(seedData("admin@mediflow.example")));
        assertThat(jdbc.queryForObject("select count(*) from local_demo_mailbox_account", Integer.class)).isZero();
    }

    @Test
    void doesNotRegisterWhenAccountIdentityNoLongerMatchesTheTrustedSeedEmail() throws Exception {
        jdbc.update("update account set username='admin@real.example', email_migrated=true where account_id=?", accountId);
        backfill.registerSeedAccounts(new ObjectMapper().readTree(seedData("demo.staff01@example.test")));

        assertThat(jdbc.queryForObject("select count(*) from local_demo_mailbox_account", Integer.class)).isZero();
    }

    private static String seedData(String staffEmail) {
        return """
                {
                  "staff": [{"key":"staff-01","email":"%s"}],
                  "patients": [],
                  "accounts": [{"key":"account-demo.admin","role":"ADMIN","staffKey":"staff-01"}]
                }
                """.formatted(staffEmail);
    }
}
