package com.mediflow.clinical.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.clinical.domain.model.OutpatientRegistration;
import com.mediflow.clinical.infrastructure.persistence.adapter.OutpatientRegistrationPersistenceAdapter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class OpenVisitPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static final LocalDate DAY = LocalDate.of(2026, 11, 10);
    static final Instant EARLY = Instant.parse("2026-11-10T01:00:00Z");
    static JdbcTemplate jdbc;
    static ClinicIntakeTestFixtures fixtures;
    static OutpatientRegistrationPersistenceAdapter adapter;
    static int queueNumber;

    @BeforeAll static void build() {
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = new JdbcTemplate(ds);
        fixtures = new ClinicIntakeTestFixtures(jdbc);
        adapter = new OutpatientRegistrationPersistenceAdapter(jdbc, new ObjectMapper().findAndRegisterModules());
    }

    /** A visit in examination since {@code startedAt} with a record in {@code recordStatus}. */
    private UUID[] inExam(ConsultationSession session, Instant startedAt, String recordStatus) {
        OutpatientRegistration registration = fixtures.confirmedWithNumber(session, ++queueNumber);
        UUID id = registration.registrationId();
        jdbc.update("""
                UPDATE outpatient_registration SET status='IN_EXAM',reservation_status='CONSUMED',checked_in_at=?,revision=revision+1
                 WHERE registration_id=?
                """, Timestamp.from(startedAt), id);
        var config = session.configuration();
        jdbc.update("UPDATE appointment SET status='IN_EXAM',care_contract_version=2,exam_price_code='OUTPATIENT_EXAM',examination_started_at=? WHERE appointment_id=?",
                Timestamp.from(startedAt), id);
        UUID record = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO medical_record(record_id,patient_id,doctor_id,department_id,examination_date,appointment_id) VALUES (?,?,?,?,?,?)
                """, record, registration.patientId(), config.doctorId(), config.departmentId(), java.sql.Date.valueOf(DAY), id);
        if ("CLOSED".equals(recordStatus)) {
            jdbc.update("""
                    UPDATE medical_record SET status='CLOSED_WITHOUT_CONCLUSION',closure_reason='OTHER',closure_note='x',
                    closed_by_staff_id=gen_random_uuid(),closed_at=now() WHERE record_id=?
                    """, record);
        }
        return new UUID[] {id, record, registration.patientId()};
    }

    @Test void doctorListsOwnOpenVisitsOldestFirst() {
        UUID doctor = UUID.randomUUID();
        UUID department = UUID.randomUUID();
        ConsultationSession session = fixtures.session(doctor, department, UUID.randomUUID(), DAY, 50);
        UUID[] later = inExam(session, EARLY.plusSeconds(600), "OPEN");
        UUID[] older = inExam(session, EARLY, "OPEN");
        inExam(fixtures.session(department, DAY), EARLY, "OPEN");
        assertThat(adapter.findOpenVisitsByDoctor(doctor, 100)).extracting(visit -> visit.recordId()).containsExactly(older[1], later[1]);
        assertThat(adapter.findOpenVisitsByDoctor(doctor, 1)).extracting(visit -> visit.recordId()).containsExactly(older[1]);
    }

    @Test void patientListCarriesTheVisitAndOnlyOpenRecords() {
        UUID department = UUID.randomUUID();
        ConsultationSession session = fixtures.session(UUID.randomUUID(), department, UUID.randomUUID(), DAY, 50);
        UUID[] open = inExam(session, EARLY, "OPEN");
        UUID[] closed = inExam(session, EARLY, "CLOSED");
        var visits = adapter.findOpenVisitsByPatient(open[2]);
        assertThat(visits).hasSize(1);
        assertThat(visits.get(0).registrationId()).isEqualTo(open[0]);
        assertThat(visits.get(0).departmentId()).isEqualTo(department);
        assertThat(visits.get(0).sessionDate()).isEqualTo(DAY);
        assertThat(visits.get(0).startedAt()).isEqualTo(EARLY);
        assertThat(adapter.findOpenVisitsByPatient(closed[2])).isEmpty();
    }
}
