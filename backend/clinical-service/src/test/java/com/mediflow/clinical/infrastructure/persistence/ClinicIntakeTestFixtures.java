package com.mediflow.clinical.infrastructure.persistence;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.clinical.domain.model.OutpatientReceptionMode;
import com.mediflow.clinical.domain.model.OutpatientRegistration;
import com.mediflow.clinical.infrastructure.persistence.adapter.ConsultationSessionPersistenceAdapter;
import com.mediflow.clinical.infrastructure.persistence.adapter.OutpatientRegistrationPersistenceAdapter;
import org.springframework.jdbc.core.JdbcTemplate;

/** Real sessions and registrations for persistence tests; rows pass every schema constraint. */
public final class ClinicIntakeTestFixtures {
    public static final UUID ACTOR = UUID.randomUUID();
    public static final Instant NOW = Instant.parse("2026-11-10T01:00:00Z");
    private final JdbcTemplate jdbc;
    private final ConsultationSessionPersistenceAdapter sessions;
    private final OutpatientRegistrationPersistenceAdapter registrations;

    public ClinicIntakeTestFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        var mapper = new ObjectMapper().findAndRegisterModules();
        this.sessions = new ConsultationSessionPersistenceAdapter(jdbc, mapper);
        this.registrations = new OutpatientRegistrationPersistenceAdapter(jdbc, mapper);
    }

    public ConsultationSession session(UUID doctorId, UUID departmentId, UUID roomId, LocalDate date, int capacity) {
        var session = ConsultationSession.open(UUID.randomUUID(), doctorId, departmentId, roomId, date,
                LocalTime.of(8, 0), LocalTime.of(11, 0), List.of(), capacity);
        return sessions.insert(session, ACTOR, null, NOW);
    }

    public ConsultationSession session(UUID departmentId, LocalDate date) {
        return session(UUID.randomUUID(), departmentId, UUID.randomUUID(), date, 50);
    }

    /** A registration still holding its seat. */
    public OutpatientRegistration registration(ConsultationSession session) {
        UUID id = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        var config = session.configuration();
        jdbc.update("""
                INSERT INTO appointment(appointment_id,patient_id,doctor_id,department_id,appointment_date,appointment_time,status)
                VALUES (?,?,?,?,?,?,'CANCELLED')
                """, id, patientId, config.doctorId(), config.departmentId(), Date.valueOf(config.sessionDate()),
                java.sql.Time.valueOf(config.startTime()));
        var registration = OutpatientRegistration.create(id, UUID.randomUUID(), patientId, session,
                OutpatientReceptionMode.AT_COUNTER, NOW);
        return registrations.insert(registration, ACTOR, null, "CREATED", NOW);
    }

    /** A registration that already carries a counter number, as if confirmed earlier. */
    public OutpatientRegistration confirmedWithNumber(ConsultationSession session, int queueNumber) {
        var registration = registration(session);
        jdbc.update("""
                UPDATE outpatient_registration SET status='CONFIRMED',reservation_status='CONFIRMED',queue_number=?,
                       transaction_id=?,clearance_id=?,payment_completed_at=?,revision=revision+1
                 WHERE registration_id=?
                """, queueNumber, UUID.randomUUID(), UUID.randomUUID(), java.sql.Timestamp.from(NOW),
                registration.registrationId());
        return registration;
    }
}
