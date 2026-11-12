package com.mediflow.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Exercises both context migrations and the unique-index race guard on real PostgreSQL. */
@Testcontainers(disabledWithoutDocker = true)
class EmailUniquenessMigrationPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void bothMigrationsPreserveLegacyRowsAndReserveNewNormalizedAddressesAtomically() throws Exception {
        String orgSchema = "org_email_" + UUID.randomUUID().toString().replace("-", "");
        String patientSchema = "patient_email_" + UUID.randomUUID().toString().replace("-", "");
        DataSource orgSource = source(orgSchema);
        DataSource patientSource = source(patientSchema);

        migrate(orgSource, orgSchema, "classpath:db/organization-migration", "22");
        migrate(patientSource, patientSchema, "classpath:db/patient-migration", "5");

        JdbcTemplate org = new JdbcTemplate(orgSource);
        JdbcTemplate patient = new JdbcTemplate(patientSource);
        UUID departmentId = UUID.randomUUID();
        org.update("INSERT INTO department (department_id, department_name, abbreviation, department_type) VALUES (?, ?, ?, ?)",
                departmentId, "Email test", "EML", "CLINICAL");

        UUID legacyStaffOne = staff(org, departmentId, "Legacy@Example.test");
        UUID legacyStaffTwo = staff(org, departmentId, " legacy@example.test ");
        UUID uniqueStaff = staff(org, departmentId, " Unique@Example.test ");
        staff(org, departmentId, "   ");
        staff(org, departmentId, null);

        UUID legacyPatientOne = patient(patient, "Legacy@Example.test", "legacy-patient-1");
        UUID legacyPatientTwo = patient(patient, " legacy@example.test ", "legacy-patient-2");
        UUID uniquePatient = patient(patient, " Unique@Example.test ", "unique-patient");
        patient(patient, "   ", "blank-patient");
        patient(patient, null, "null-patient");

        migrate(orgSource, orgSchema, "classpath:db/organization-migration", "23");
        migrate(patientSource, patientSchema, "classpath:db/patient-migration", "6");

        assertThat(org.queryForObject("SELECT count(*) FROM staff WHERE staff_id IN (?, ?) AND email IS NOT NULL",
                Integer.class, legacyStaffOne, legacyStaffTwo)).isEqualTo(2);
        assertThat(org.queryForObject("SELECT count(*) FROM staff WHERE staff_id IN (?, ?) AND email_unique_key IS NULL",
                Integer.class, legacyStaffOne, legacyStaffTwo)).isEqualTo(2);
        assertThat(org.queryForObject("SELECT email_unique_key FROM staff WHERE staff_id=?", String.class, uniqueStaff))
                .isEqualTo("unique@example.test");
        assertThat(org.queryForObject("SELECT count(*) FROM staff WHERE (email IS NULL OR btrim(email) = '') AND email_unique_key IS NULL",
                Integer.class)).isGreaterThanOrEqualTo(2);

        assertThat(patient.queryForObject("SELECT count(*) FROM patient WHERE patient_id IN (?, ?) AND email IS NOT NULL",
                Integer.class, legacyPatientOne, legacyPatientTwo)).isEqualTo(2);
        assertThat(patient.queryForObject("SELECT count(*) FROM patient WHERE patient_id IN (?, ?) AND email_unique_key IS NULL",
                Integer.class, legacyPatientOne, legacyPatientTwo)).isEqualTo(2);
        assertThat(patient.queryForObject("SELECT email_unique_key FROM patient WHERE patient_id=?", String.class, uniquePatient))
                .isEqualTo("unique@example.test");
        assertThat(patient.queryForObject("SELECT count(*) FROM patient WHERE (email IS NULL OR btrim(email) = '') AND email_unique_key IS NULL",
                Integer.class)).isGreaterThanOrEqualTo(2);

        assertThat(concurrentPatientInserts(patient)).containsExactlyInAnyOrder(true, false);
        assertThat(concurrentStaffInserts(org, departmentId)).containsExactlyInAnyOrder(true, false);
    }

    private static DataSource source(String schema) {
        var source = new DriverManagerDataSource();
        source.setDriverClassName("org.postgresql.Driver");
        source.setUrl(POSTGRES.getJdbcUrl() + "&currentSchema=" + schema);
        source.setUsername(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        return source;
    }

    private static void migrate(DataSource source, String schema, String location, String target) {
        Flyway.configure().dataSource(source).locations(location)
                .schemas(schema).defaultSchema(schema)
                .target(MigrationVersion.fromVersion(target)).load().migrate();
    }

    private static UUID staff(JdbcTemplate jdbc, UUID departmentId, String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO staff (staff_id, full_name, department_id, job_title, email) VALUES (?, ?, ?, ?, ?)",
                id, "Legacy staff", departmentId, "NURSE", email);
        return id;
    }

    private static UUID patient(JdbcTemplate jdbc, String email, String suffix) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO patient (patient_id, full_name, date_of_birth, gender, identity_number,
                                    email, created_at, archived)
                VALUES (?, ?, DATE '1990-01-01', 'M', ?, ?, now(), FALSE)
                """, id, "Legacy patient", suffix, email);
        return id;
    }

    private static java.util.List<Boolean> concurrentPatientInserts(JdbcTemplate jdbc) throws Exception {
        String address = "race@example.test";
        return race(() -> jdbc.update("""
                        INSERT INTO patient (patient_id, full_name, date_of_birth, gender, identity_number,
                                             email, email_unique_key, created_at, archived)
                        VALUES (?, 'Race patient', DATE '1990-01-01', 'M', ?, ?, ?, now(), FALSE)
                        """, UUID.randomUUID(), "race-" + UUID.randomUUID().toString().substring(0, 8), "Race@Example.test", address),
                () -> jdbc.update("""
                        INSERT INTO patient (patient_id, full_name, date_of_birth, gender, identity_number,
                                             email, email_unique_key, created_at, archived)
                        VALUES (?, 'Race patient', DATE '1990-01-01', 'F', ?, ?, ?, now(), FALSE)
                        """, UUID.randomUUID(), "race-" + UUID.randomUUID().toString().substring(0, 8), " race@example.test ", address));
    }

    private static java.util.List<Boolean> concurrentStaffInserts(JdbcTemplate jdbc, UUID departmentId) throws Exception {
        String address = "race-staff@example.test";
        return race(() -> jdbc.update("""
                        INSERT INTO staff (staff_id, full_name, department_id, job_title, email, email_unique_key, identity_number)
                        VALUES (?, 'Race staff', ?, 'NURSE', ?, ?, ?)
                        """, UUID.randomUUID(), departmentId, "Race-Staff@Example.test", address, "000000000001"),
                () -> jdbc.update("""
                        INSERT INTO staff (staff_id, full_name, department_id, job_title, email, email_unique_key, identity_number)
                        VALUES (?, 'Race staff', ?, 'NURSE', ?, ?, ?)
                        """, UUID.randomUUID(), departmentId, " race-staff@example.test ", address, "000000000002"));
    }

    private static java.util.List<Boolean> race(RunnableSql first, RunnableSql second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> insert(start, first));
            var b = pool.submit(() -> insert(start, second));
            start.countDown();
            return java.util.List.of(a.get(), b.get());
        }
    }

    private static boolean insert(CountDownLatch start, RunnableSql sql) throws Exception {
        start.await();
        try {
            sql.run();
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
    }

    @FunctionalInterface
    private interface RunnableSql {
        void run();
    }
}
