package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mediflow.clinical.application.dto.authority.RecordReadDecision;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.response.RecordCapabilitiesDTO;
import com.mediflow.clinical.application.port.out.AppointmentRepositoryPort;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.OutpatientMvpMode;
import com.mediflow.clinical.application.port.out.RecordAccessDeclarationPort;
import com.mediflow.clinical.application.port.out.RecordAccessLogPort;
import com.mediflow.clinical.application.port.out.RecordAccessPolicyMode;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.AppointmentStatus;
import com.mediflow.clinical.domain.model.Diagnosis;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.MedicalRecordStatus;
import com.mediflow.clinical.domain.model.RecordDisposition;

class RecordCapabilitiesTest {
    private static final Instant CREATED = Instant.parse("2026-11-01T03:00:00Z");
    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID DEPARTMENT = UUID.randomUUID();
    private static final UUID ASSIGNED_DOCTOR = UUID.randomUUID();
    private static final UUID APPOINTMENT = UUID.randomUUID();

    private final MedicalRecordRepositoryPort records = mock(MedicalRecordRepositoryPort.class);
    private final AppointmentRepositoryPort appointments = mock(AppointmentRepositoryPort.class);
    private final RecordReadPolicy readPolicy = mock(RecordReadPolicy.class);
    private final CurrentClinicalActorPort actors = mock(CurrentClinicalActorPort.class);
    private final CorrelationIdProvider correlations = mock(CorrelationIdProvider.class);

    @BeforeEach
    void setUp() {
        when(correlations.currentOrCreate()).thenReturn(UUID.randomUUID());
        when(readPolicy.forRecord(any(), any(), any(), any())).thenReturn(RecordReadDecision.allow("CARE_ASSIGNMENT"));
    }

    @Test
    void assignedDoctorInExamCanWrite() {
        MedicalRecord record = openRecord();
        appointment(AppointmentStatus.IN_EXAM);
        actor(ASSIGNED_DOCTOR, "DOCTOR");

        RecordCapabilitiesDTO capabilities = service(true, true).capabilities(record.getRecordId());

        assertThat(capabilities.canRead()).isTrue();
        assertThat(capabilities.readBasis()).isEqualTo("CARE_ASSIGNMENT");
        assertThat(capabilities.canWriteContent()).isTrue();
        assertThat(capabilities.canComplete()).isTrue();
        assertThat(capabilities.canRequestAmendment()).isFalse();
        assertThat(capabilities.writeBlockedReason()).isNull();
    }

    @Test
    void otherDoctorSameDepartmentCannotWrite() {
        MedicalRecord record = openRecord();
        appointment(AppointmentStatus.IN_EXAM);
        actor(UUID.randomUUID(), "DOCTOR");

        RecordCapabilitiesDTO capabilities = service(true, true).capabilities(record.getRecordId());

        assertThat(capabilities.canWriteContent()).isFalse();
        assertThat(capabilities.canComplete()).isFalse();
        assertThat(capabilities.writeBlockedReason()).isEqualTo("NOT_ASSIGNED_DOCTOR");
    }

    @Test
    void assignedDoctorOutsideExamCannotWrite() {
        MedicalRecord record = openRecord();
        appointment(AppointmentStatus.READY_FOR_EXAM);
        actor(ASSIGNED_DOCTOR, "DOCTOR");

        RecordCapabilitiesDTO capabilities = service(true, true).capabilities(record.getRecordId());

        assertThat(capabilities.canWriteContent()).isFalse();
        assertThat(capabilities.writeBlockedReason()).isEqualTo("NOT_IN_EXAM");
    }

    @Test
    void completedRecordCannotBeWrittenByAuthor() {
        MedicalRecord record = store(MedicalRecord.restore(UUID.randomUUID(), PATIENT, ASSIGNED_DOCTOR, DEPARTMENT,
                LocalDate.of(2026, 11, 1), "symptoms", APPOINTMENT, List.of(Diagnosis.create("Viem hong", "mo ta", "J02")), CREATED, CREATED,
                MedicalRecordStatus.COMPLETED, RecordDisposition.values()[0], null, CREATED));
        appointment(AppointmentStatus.COMPLETED);
        actor(ASSIGNED_DOCTOR, "DOCTOR");

        RecordCapabilitiesDTO capabilities = service(true, true).capabilities(record.getRecordId());

        assertThat(capabilities.canWriteContent()).isFalse();
        assertThat(capabilities.canComplete()).isFalse();
        assertThat(capabilities.writeBlockedReason()).isEqualTo("RECORD_COMPLETED");
    }

    @Test
    void deniedReaderReportsNoReadCapability() {
        MedicalRecord record = openRecord();
        appointment(AppointmentStatus.IN_EXAM);
        actor(UUID.randomUUID(), "NURSE");
        when(readPolicy.forRecord(any(), any(), any(), any()))
                .thenReturn(RecordReadDecision.deny("RECORD_ACCESS_PURPOSE_REQUIRED"));

        RecordCapabilitiesDTO capabilities = service(true, true).capabilities(record.getRecordId());

        assertThat(capabilities.canRead()).isFalse();
        assertThat(capabilities.readBasis()).isNull();
        assertThat(capabilities.canWriteContent()).isFalse();
    }

    @Test
    void flagOffReportsLegacyRole() {
        MedicalRecord record = openRecord();
        appointment(AppointmentStatus.IN_EXAM);
        actor(UUID.randomUUID(), "DOCTOR");

        RecordCapabilitiesDTO capabilities = service(false, false).capabilities(record.getRecordId());

        assertThat(capabilities.canRead()).isTrue();
        assertThat(capabilities.readBasis()).isEqualTo("LEGACY_ROLE");
        assertThat(capabilities.canWriteContent()).isTrue();
    }

    private RecordAccessApplicationService service(boolean policyEnabled, boolean mvpEnabled) {
        return new RecordAccessApplicationService(records, appointments, mock(RecordAccessDeclarationPort.class),
                mock(RecordAccessLogPort.class), readPolicy, actors, correlations,
                new OutpatientMvpMode(mvpEnabled), new RecordAccessPolicyMode(policyEnabled));
    }

    private MedicalRecord openRecord() {
        return store(MedicalRecord.restore(UUID.randomUUID(), PATIENT, ASSIGNED_DOCTOR, DEPARTMENT,
                LocalDate.of(2026, 11, 1), "symptoms", APPOINTMENT, List.<Diagnosis>of(), CREATED, null));
    }

    private MedicalRecord store(MedicalRecord record) {
        when(records.findById(record.getRecordId())).thenReturn(Optional.of(record));
        return record;
    }

    private void appointment(AppointmentStatus status) {
        when(appointments.findById(APPOINTMENT)).thenReturn(Optional.of(Appointment.restore(APPOINTMENT, PATIENT,
                ASSIGNED_DOCTOR, DEPARTMENT, LocalDate.of(2026, 11, 1), LocalTime.of(9, 0), status, "visit", CREATED,
                null, (short) 0, UUID.randomUUID(), CREATED, null, null, null, null,
                status == AppointmentStatus.COMPLETED ? CREATED : null)));
    }

    private void actor(UUID staffId, String role) {
        when(actors.current()).thenReturn(new ActorIdentity(UUID.randomUUID(), staffId, null, role));
    }
}
