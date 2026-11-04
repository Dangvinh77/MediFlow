package com.mediflow.organization.infrastructure.config;

import com.mediflow.organization.application.port.out.PatientAccountIdentityPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PatientAccountEmailBackfillTest {
    private JdbcTemplate jdbc;
    private PatientAccountIdentityPort patients;
    private PatientAccountEmailBackfill backfill;
    private final UUID accountA = UUID.randomUUID();
    private final UUID accountB = UUID.randomUUID();
    private final UUID patientA = UUID.randomUUID();
    private final UUID patientB = UUID.randomUUID();

    @BeforeEach
    void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("create table account(account_id uuid primary key,patient_id uuid,role varchar(30),username varchar(254),email_migrated boolean)");
        jdbc.update("insert into account values (?,?,'PATIENT','legacy-identity-a',false)", accountA, patientA);
        jdbc.update("insert into account values (?,?,'PATIENT','legacy-identity-b',false)", accountB, patientB);
        patients = mock(PatientAccountIdentityPort.class);
        backfill = new PatientAccountEmailBackfill(source, patients);
    }

    @Test
    void leavesEveryUnmigratedPatientBlockedWhenNormalizedContactEmailIsDuplicated() {
        when(patients.email(patientA)).thenReturn("Person@example.test");
        when(patients.email(patientB)).thenReturn(" person@example.test ");

        backfill.run(new DefaultApplicationArguments(new String[0]));

        assertThat(username(accountA)).isEqualTo("legacy-identity-a");
        assertThat(username(accountB)).isEqualTo("legacy-identity-b");
        assertThat(migrated(accountA)).isFalse();
        assertThat(migrated(accountB)).isFalse();
    }

    @Test
    void migratesDistinctValidEmails() {
        when(patients.email(patientA)).thenReturn("PersonA@example.test");
        when(patients.email(patientB)).thenReturn("PersonB@example.test");

        backfill.run(new DefaultApplicationArguments(new String[0]));

        assertThat(username(accountA)).isEqualTo("persona@example.test");
        assertThat(username(accountB)).isEqualTo("personb@example.test");
        assertThat(migrated(accountA)).isTrue();
        assertThat(migrated(accountB)).isTrue();
    }

    private String username(UUID accountId) {
        return jdbc.queryForObject("select username from account where account_id=?", String.class, accountId);
    }

    private boolean migrated(UUID accountId) {
        return jdbc.queryForObject("select email_migrated from account where account_id=?", Boolean.class, accountId);
    }
}
