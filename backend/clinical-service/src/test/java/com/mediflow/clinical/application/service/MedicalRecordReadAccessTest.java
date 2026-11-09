package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.mediflow.clinical.application.dto.authority.CurrentDoctorAuthoritySnapshot;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.response.MedicalRecordDTO;
import com.mediflow.clinical.application.mapper.ClinicalDtoMapper;
import com.mediflow.clinical.application.port.out.AppointmentRepositoryPort;
import com.mediflow.clinical.application.port.out.ClinicalEventPublisherPort;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.PatientLookupPort;
import com.mediflow.clinical.application.port.out.RecordAccessDeclarationPort;
import com.mediflow.clinical.application.port.out.RecordAccessLogPort;
import com.mediflow.clinical.application.port.out.SchedulingAuthorityPort;
import com.mediflow.clinical.application.port.out.StaffLookupPort;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.AppointmentStatus;
import com.mediflow.clinical.domain.model.Diagnosis;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.RecordAccessDeclaration;
import com.mediflow.clinical.domain.model.RecordAccessLogEntry;
import com.mediflow.common.exception.ForbiddenOperationException;

class MedicalRecordReadAccessTest {
    private static final Instant CREATED = Instant.parse("2026-11-01T03:00:00Z");
    private static final UUID CORRELATION = UUID.randomUUID();
    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID CARDIOLOGY = UUID.randomUUID();
    private static final UUID GASTRO = UUID.randomUUID();
    private static final UUID RECORD_DOCTOR = UUID.randomUUID();

    private final AppointmentRepositoryPort appointments = mock(AppointmentRepositoryPort.class);
    private final MedicalRecordRepositoryPort records = mock(MedicalRecordRepositoryPort.class);
    private final CurrentClinicalActorPort actors = mock(CurrentClinicalActorPort.class);
    private final CorrelationIdProvider correlations = mock(CorrelationIdProvider.class);
    private final SchedulingAuthorityPort authority = mock(SchedulingAuthorityPort.class);
    private final RecordAccessDeclarationPort declarations = mock(RecordAccessDeclarationPort.class);
    private final RecordAccessLogPort log = mock(RecordAccessLogPort.class);
    private final List<RecordAccessLogEntry> logged = new ArrayList<>();
    private final List<Appointment> patientAppointments = new ArrayList<>();
    private final ClinicalDtoMapper mapper = Mappers.getMapper(ClinicalDtoMapper.class);

    private final MedicalRecord cardiology = record(CARDIOLOGY);
    private final MedicalRecord gastro = record(GASTRO);

    private MedicalRecordApplicationService service(boolean policyEnabled) {
        RecordReadGuard guard = new RecordReadGuard(new RecordReadPolicy(appointments, declarations, authority), log,
                actors, correlations);
        return new MedicalRecordApplicationService(appointments, records, mock(PatientLookupPort.class),
                mock(StaffLookupPort.class), mock(ClinicalEventPublisherPort.class), mapper, correlations, actors,
                false, guard, policyEnabled);
    }

    @BeforeEach
    void setUp() {
        when(correlations.currentOrCreate()).thenReturn(CORRELATION);
        when(appointments.findByPatient(PATIENT)).thenReturn(patientAppointments);
        when(declarations.findActive(any(), any(), any())).thenReturn(Optional.empty());
        when(records.findById(cardiology.getRecordId())).thenReturn(Optional.of(cardiology));
        when(records.findById(gastro.getRecordId())).thenReturn(Optional.of(gastro));
        when(records.findByPatient(PATIENT)).thenReturn(List.of(cardiology, gastro));
        doAnswer(call -> logged.add(call.getArgument(0))).when(log).append(any());
    }

    @Test
    void flagOffKeepsLegacyBehaviour() {
        actor("DOCTOR", CARDIOLOGY);

        MedicalRecordDTO dto = service(false).getById(cardiology.getRecordId());

        assertThat(dto.recordId()).isEqualTo(cardiology.getRecordId());
        verify(authority, never()).currentDoctorAuthority(any(), any());
        assertThat(logged).isEmpty();
    }

    @Test
    void allowedReadWritesOneLogRow() {
        ActorIdentity actor = actor("DOCTOR", CARDIOLOGY);
        visit(CARDIOLOGY);

        MedicalRecordDTO dto = service(true).getById(cardiology.getRecordId());

        assertThat(dto.symptoms()).isEqualTo("symptoms");
        assertThat(logged).singleElement().satisfies(entry -> {
            assertThat(entry.actorAccountId()).isEqualTo(actor.accountId());
            assertThat(entry.actorStaffId()).isEqualTo(actor.staffId());
            assertThat(entry.actorRole()).isEqualTo("DOCTOR");
            assertThat(entry.patientId()).isEqualTo(PATIENT);
            assertThat(entry.recordId()).isEqualTo(cardiology.getRecordId());
            assertThat(entry.action()).isEqualTo("READ");
            assertThat(entry.outcome()).isEqualTo("ALLOWED");
            assertThat(entry.basis()).isEqualTo("DEPARTMENT_ENCOUNTER");
            assertThat(entry.correlationId()).isEqualTo(CORRELATION.toString());
        });
    }

    @Test
    void deniedReadWritesLogThenThrows403() {
        actor("DOCTOR", CARDIOLOGY);

        assertThatThrownBy(() -> service(true).getById(cardiology.getRecordId()))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("code", "RECORD_ACCESS_PURPOSE_REQUIRED");

        assertThat(logged).singleElement().satisfies(entry -> {
            assertThat(entry.outcome()).isEqualTo("DENIED");
            assertThat(entry.basis()).isNull();
            assertThat(entry.recordId()).isEqualTo(cardiology.getRecordId());
        });
    }

    @Test
    void logFailureFailsClosed() {
        actor("DOCTOR", CARDIOLOGY);
        visit(CARDIOLOGY);
        doThrow(new IllegalStateException("database down")).when(log).append(any());

        assertThatThrownBy(() -> service(true).getById(cardiology.getRecordId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void listWritesOneLogRowWithNullRecordId() {
        actor("DOCTOR", CARDIOLOGY);
        visit(CARDIOLOGY);

        List<MedicalRecordDTO> result = service(true).byPatient(PATIENT);

        assertThat(result).hasSize(2);
        assertThat(logged).singleElement().satisfies(entry -> {
            assertThat(entry.action()).isEqualTo("LIST");
            assertThat(entry.recordId()).isNull();
            assertThat(entry.outcome()).isEqualTo("ALLOWED");
        });
    }

    @Test
    void nurseListHidesOtherDepartments() {
        actor("NURSE", CARDIOLOGY);
        visit(CARDIOLOGY);

        List<MedicalRecordDTO> result = service(true).byPatient(PATIENT);

        assertThat(result).extracting(MedicalRecordDTO::recordId).containsExactly(cardiology.getRecordId());
        assertThat(result.get(0).symptoms()).isEqualTo("symptoms");
    }

    @Test
    void sameNurseStillNeedsPurposeToReadThatDepartmentsRecord() {
        actor("NURSE", UUID.randomUUID());
        visit(CARDIOLOGY);

        assertThatThrownBy(() -> service(true).getById(cardiology.getRecordId()))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("code", "RECORD_ACCESS_PURPOSE_REQUIRED");
    }

    @Test
    void adminEmptyPatientReturnsEmptyAfterDeclarationCheck() {
        ActorIdentity admin = adminWithoutStaff();
        UUID newPatient = UUID.randomUUID();
        when(appointments.findByPatient(newPatient)).thenReturn(List.of());
        when(records.findByPatient(newPatient)).thenReturn(List.of());

        assertThatThrownBy(() -> service(true).byPatient(newPatient))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasFieldOrPropertyWithValue("code", "RECORD_ACCESS_PURPOSE_REQUIRED");

        when(declarations.findActive(any(), any(), any())).thenReturn(Optional.of(new RecordAccessDeclaration(
                UUID.randomUUID(), admin.accountId(), null, "ADMIN", newPatient, "RECORD_MANAGEMENT",
                "kiem tra ho so", CREATED, Instant.now().plusSeconds(3600))));

        assertThat(service(true).byPatient(newPatient)).isEmpty();
    }

    private ActorIdentity actor(String role, UUID department) {
        ActorIdentity actor = new ActorIdentity(UUID.randomUUID(), UUID.randomUUID(), null, role);
        when(actors.current()).thenReturn(actor);
        when(authority.currentDoctorAuthority(actor.accountId(), CORRELATION.toString())).thenReturn(
                new CurrentDoctorAuthoritySnapshot(actor.accountId(), true, true, role, actor.staffId(), true,
                        department, true, CREATED));
        return actor;
    }

    private ActorIdentity adminWithoutStaff() {
        ActorIdentity admin = new ActorIdentity(UUID.randomUUID(), null, null, "ADMIN");
        when(actors.current()).thenReturn(admin);
        when(authority.currentDoctorAuthority(admin.accountId(), CORRELATION.toString())).thenReturn(
                new CurrentDoctorAuthoritySnapshot(admin.accountId(), true, true, "ADMIN", null, false, null, false,
                        CREATED));
        return admin;
    }

    private void visit(UUID department) {
        patientAppointments.add(Appointment.restore(UUID.randomUUID(), PATIENT, UUID.randomUUID(), department,
                LocalDate.of(2026, 11, 9), LocalTime.of(9, 0), AppointmentStatus.PENDING, "visit", CREATED, null));
    }

    private static MedicalRecord record(UUID department) {
        return MedicalRecord.restore(UUID.randomUUID(), PATIENT, RECORD_DOCTOR, department, LocalDate.of(2026, 11, 1),
                "symptoms", UUID.randomUUID(), List.<Diagnosis>of(), CREATED, null);
    }
}
