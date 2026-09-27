package com.mediflow.clinical.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.clinical.domain.model.AdmissionPriority;
import com.mediflow.clinical.domain.model.AdmissionReferral;
import com.mediflow.clinical.domain.model.CareEpisodeType;
import com.mediflow.clinical.domain.model.ClearancePurpose;
import com.mediflow.clinical.domain.model.ExamClearance;
import com.mediflow.clinical.infrastructure.persistence.adapter.AdmissionReferralPersistenceAdapter;
import com.mediflow.clinical.infrastructure.persistence.adapter.ExamClearancePersistenceAdapter;
import com.mediflow.common.exception.DuplicateResourceException;

@DataJpaTest
@Import({ExamClearancePersistenceAdapter.class, AdmissionReferralPersistenceAdapter.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ClinicalCareFinancePersistenceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ExamClearancePersistenceAdapter clearances;

    @Autowired
    private AdmissionReferralPersistenceAdapter referrals;

    @Test
    void appointment_v1WithoutPriceCode_violatesCheckConstraint() {
        assertThatThrownBy(() -> insertAppointment((short) 1, null, "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void appointment_examStateWithoutClearanceOrOverride_violatesCheckConstraint() {
        assertThatThrownBy(() -> insertAppointment((short) 1, "OUTPATIENT_EXAM", "IN_EXAM"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void record_completedWithoutDisposition_violatesCheckConstraint() {
        UUID recordId = UUID.randomUUID();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO medical_record (
                    record_id, patient_id, doctor_id, department_id, examination_date,
                    status, completed_at
                ) VALUES (?, ?, ?, ?, ?, 'COMPLETED', ?)
                """, recordId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(), Instant.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void clearance_duplicateEventClaimsAndPersistsOnce() {
        UUID appointmentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ExamClearance clearance = ExamClearance.grant(eventId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), appointmentId, null, UUID.randomUUID(),
                CareEpisodeType.OUTPATIENT_VISIT, appointmentId, ClearancePurpose.EXAM,
                new BigDecimal("150000.00"), "VND", null, false, Instant.now());

        assertThat(clearances.claimAndSave(clearance)).isTrue();
        assertThat(clearances.claimAndSave(clearance)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam_clearance WHERE event_id = ?",
                Integer.class, eventId)).isEqualTo(1);
    }

    @Test
    void admissionReferral_duplicateRecord_usesDatabaseUniqueness() {
        UUID recordId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO medical_record (
                    record_id, patient_id, doctor_id, department_id, examination_date, status
                ) VALUES (?, ?, ?, ?, ?, 'OPEN')
                """, recordId, patientId, UUID.randomUUID(), departmentId, LocalDate.now());
        AdmissionReferral first = AdmissionReferral.request(recordId, patientId, departmentId,
                UUID.randomUUID(), "Needs inpatient observation", AdmissionPriority.URGENT, false, Instant.now());
        AdmissionReferral duplicate = AdmissionReferral.request(recordId, patientId, departmentId,
                UUID.randomUUID(), "Same record referral", AdmissionPriority.ROUTINE, false, Instant.now());

        referrals.save(first);

        assertThatThrownBy(() -> referrals.save(duplicate))
                .isInstanceOf(DuplicateResourceException.class)
                .hasFieldOrPropertyWithValue("code", "CLINICAL_ADMISSION_REFERRAL_CONFLICT");
    }

    private void insertAppointment(short contractVersion, String priceCode, String status) {
        jdbc.update("""
                INSERT INTO appointment (
                    appointment_id, patient_id, doctor_id, department_id, appointment_date,
                    appointment_time, status, care_contract_version, exam_price_code
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(), LocalTime.NOON, status, contractVersion, priceCode);
    }
}
