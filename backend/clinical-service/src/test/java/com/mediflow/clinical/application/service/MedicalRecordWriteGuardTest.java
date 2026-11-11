package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;

import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.request.CreateRecordRequest;
import com.mediflow.clinical.application.dto.request.AddDiagnosisRequest;
import com.mediflow.clinical.application.dto.request.UpdateRecordRequest;
import com.mediflow.clinical.application.port.out.AppointmentRepositoryPort;
import com.mediflow.clinical.application.port.out.ClinicalEventPublisherPort;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.PatientLookupPort;
import com.mediflow.clinical.application.port.out.StaffLookupPort;
import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.AppointmentStatus;
import com.mediflow.clinical.domain.model.Diagnosis;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.MedicalRecordStatus;
import com.mediflow.clinical.domain.model.RecordDisposition;
import com.mediflow.common.exception.ForbiddenOperationException;

class MedicalRecordWriteGuardTest {
    private static final Instant CREATED = Instant.parse("2026-11-01T03:00:00Z");
    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID CARDIOLOGY = UUID.randomUUID();
    private static final UUID GASTRO = UUID.randomUUID();
    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID APPOINTMENT = UUID.randomUUID();

    private final AppointmentRepositoryPort appointments = mock(AppointmentRepositoryPort.class);
    private final MedicalRecordRepositoryPort records = mock(MedicalRecordRepositoryPort.class);
    private final PatientLookupPort patients = mock(PatientLookupPort.class);
    private final StaffLookupPort staff = mock(StaffLookupPort.class);
    private final CurrentClinicalActorPort actors = mock(CurrentClinicalActorPort.class);
    private final CorrelationIdProvider correlations = mock(CorrelationIdProvider.class);

    private MedicalRecordApplicationService service(boolean mvp, boolean policy) {
        return new MedicalRecordApplicationService(appointments, records, patients, staff,
                mock(ClinicalEventPublisherPort.class), Mappers.getMapper(com.mediflow.clinical.application.mapper.ClinicalDtoMapper.class),
                correlations, actors, mvp, mock(RecordReadGuard.class), policy);
    }

    @BeforeEach
    void setUp() {
        when(correlations.currentOrCreate()).thenReturn(UUID.randomUUID());
        when(records.save(any())).thenAnswer(call -> call.getArgument(0));
        when(staff.departmentOf(AUTHOR)).thenReturn(Optional.of(CARDIOLOGY));
    }

    @Test
    void sameDepartmentDoctorCannotUpdate() {
        MedicalRecord record = inExamRecord();
        UUID colleague = doctorIn(CARDIOLOGY);

        assertNotAssigned(() -> service(true, true).update(record.getRecordId(), update()));
        verify(staff, never()).departmentOf(colleague);
    }

    @Test
    void otherDepartmentDoctorCannotUpdate() {
        MedicalRecord record = inExamRecord();
        doctorIn(GASTRO);

        assertNotAssigned(() -> service(true, true).update(record.getRecordId(), update()));
    }

    @Test
    void previousTreatingDoctorCannotUpdateNewVisit() {
        MedicalRecord record = inExamRecord();
        UUID previousDoctor = doctorIn(CARDIOLOGY);
        Appointment olderVisit = appointment(previousDoctor, AppointmentStatus.COMPLETED);
        when(appointments.findByPatient(PATIENT)).thenReturn(List.of(olderVisit));

        assertNotAssigned(() -> service(true, true).update(record.getRecordId(), update()));
    }

    @Test
    void authorCannotUpdateCompletedRecord() {
        MedicalRecord record = store(MedicalRecord.restore(UUID.randomUUID(), PATIENT, AUTHOR, CARDIOLOGY,
                LocalDate.of(2026, 11, 1), "symptoms", APPOINTMENT, List.of(Diagnosis.create("Viem hong", "mo ta", "J02")),
                CREATED, CREATED, MedicalRecordStatus.COMPLETED, RecordDisposition.values()[0], null, CREATED));
        actor(AUTHOR, "DOCTOR");

        assertThatThrownBy(() -> service(false, true).update(record.getRecordId(), update()))
                .isInstanceOf(InvalidClinicalDataException.class)
                .hasFieldOrPropertyWithValue("code", "CLINICAL_INVALID_STATUS_TRANSITION");
        verify(records, never()).save(any());
    }

    @Test
    void declaredPurposeGrantsNoWrite() {
        MedicalRecord record = openRecord();
        doctorIn(CARDIOLOGY);

        assertNotAssigned(() -> service(false, true).update(record.getRecordId(), update()));
    }

    @Test
    void nonMvpModeWithFlagRequiresRecordAuthor() {
        MedicalRecord record = openRecord();
        doctorIn(CARDIOLOGY);

        assertNotAssigned(() -> service(false, true).update(record.getRecordId(), update()));
        assertNotAssigned(() -> service(false, true).addDiagnosis(record.getRecordId(), null));
    }

    @Test
    void recordKeepsOriginalDoctorAfterUpdate() {
        MedicalRecord record = inExamRecord();
        actor(AUTHOR, "DOCTOR");

        service(true, true).update(record.getRecordId(), update());

        ArgumentCaptor<MedicalRecord> saved = ArgumentCaptor.forClass(MedicalRecord.class);
        verify(records).save(saved.capture());
        assertThat(saved.getValue().getDoctorId()).isEqualTo(AUTHOR);
        assertThat(saved.getValue().getSymptoms()).isEqualTo("updated symptoms");
    }

    @Test
    void previousDoctorCannotWriteAfterHandover() {
        MedicalRecord record = inExamRecord();
        UUID receiver = UUID.randomUUID();
        record.handOverTo(receiver, CREATED);
        actor(AUTHOR, "DOCTOR");

        assertNotAssigned(() -> service(true, true).update(record.getRecordId(), update()));
    }

    @Test
    void receiverWritesAfterHandoverAndRecordKeepsOpeningDoctor() {
        MedicalRecord record = inExamRecord();
        UUID receiver = UUID.randomUUID();
        when(staff.departmentOf(receiver)).thenReturn(Optional.of(CARDIOLOGY));
        record.handOverTo(receiver, CREATED);
        actor(receiver, "DOCTOR");

        service(true, true).update(record.getRecordId(), update());

        ArgumentCaptor<MedicalRecord> saved = ArgumentCaptor.forClass(MedicalRecord.class);
        verify(records).save(saved.capture());
        assertThat(saved.getValue().getDoctorId()).isEqualTo(AUTHOR);
        assertThat(saved.getValue().responsibleDoctorId()).isEqualTo(receiver);
    }

    @Test
    void secondRecordForAppointmentIsRejected() {
        MedicalRecord existing = openRecord();
        when(patients.exists(PATIENT)).thenReturn(true);
        Appointment appointment = appointment(AUTHOR, AppointmentStatus.ARRIVED);
        when(appointments.findByIdForUpdate(APPOINTMENT)).thenReturn(Optional.of(appointment));
        when(records.findByAppointmentId(APPOINTMENT)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service(false, false).create(new CreateRecordRequest(PATIENT, AUTHOR, CARDIOLOGY,
                LocalDate.of(2026, 11, 1), "symptoms", APPOINTMENT,
                List.of(new AddDiagnosisRequest("Viem hong", "mo ta", "J02")))))
                .isInstanceOf(InvalidClinicalDataException.class)
                .hasFieldOrPropertyWithValue("code", "RECORD_DUPLICATE_APPOINTMENT");
        verify(records, never()).save(any());
    }

    private void assertNotAssigned(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("code", "CLINICAL_DOCTOR_NOT_ASSIGNED");
        verify(records, never()).save(any());
    }

    private UUID doctorIn(UUID department) {
        UUID doctor = UUID.randomUUID();
        when(staff.departmentOf(doctor)).thenReturn(Optional.of(department));
        actor(doctor, "DOCTOR");
        return doctor;
    }

    private void actor(UUID staffId, String role) {
        when(actors.current()).thenReturn(new ActorIdentity(UUID.randomUUID(), staffId, null, role));
    }

    private static UpdateRecordRequest update() {
        return new UpdateRecordRequest("updated symptoms");
    }

    private MedicalRecord inExamRecord() {
        MedicalRecord record = openRecord();
        when(appointments.findByIdForUpdate(APPOINTMENT))
                .thenReturn(Optional.of(appointment(AUTHOR, AppointmentStatus.IN_EXAM)));
        return record;
    }

    private MedicalRecord openRecord() {
        return store(MedicalRecord.restore(UUID.randomUUID(), PATIENT, AUTHOR, CARDIOLOGY, LocalDate.of(2026, 11, 1),
                "symptoms", APPOINTMENT, List.<Diagnosis>of(), CREATED, null));
    }

    private MedicalRecord store(MedicalRecord record) {
        when(records.findByIdForUpdate(record.getRecordId())).thenReturn(Optional.of(record));
        return record;
    }

    private static Appointment appointment(UUID doctor, AppointmentStatus status) {
        return Appointment.restore(APPOINTMENT, PATIENT, doctor, CARDIOLOGY, LocalDate.of(2026, 11, 1),
                LocalTime.of(9, 0), status, "visit", CREATED, null, (short) 2, UUID.randomUUID(), CREATED, null,
                "OUTPATIENT_EXAM", null, null, status == AppointmentStatus.COMPLETED ? CREATED : null);
    }
}
