package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.port.out.AppointmentRepositoryPort;
import com.mediflow.clinical.application.port.out.ClinicalLabOrderRepositoryPort;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.OutpatientMvpMode;
import com.mediflow.clinical.application.port.out.StaffLookupPort;
import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.AppointmentStatus;
import com.mediflow.clinical.domain.model.ClinicalLabOrder;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.MedicalRecordStatus;
import com.mediflow.clinical.application.dto.request.CreateClinicalLabOrderRequest;
import com.mediflow.common.exception.ForbiddenOperationException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClinicalLabOrderApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-000000001003");
    private static final UUID DOCTOR = UUID.fromString("00000000-0000-0000-0000-000000001004");
    private static final UUID DEPARTMENT = UUID.fromString("00000000-0000-0000-0000-000000001005");
    private static final UUID APPOINTMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000001006");

    private final AppointmentRepositoryPort appointments = mock(AppointmentRepositoryPort.class);
    private final MedicalRecordRepositoryPort records = mock(MedicalRecordRepositoryPort.class);
    private final ClinicalLabOrderRepositoryPort orders = mock(ClinicalLabOrderRepositoryPort.class);
    private final CurrentClinicalActorPort actors = mock(CurrentClinicalActorPort.class);
    private final StaffLookupPort staff = mock(StaffLookupPort.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final MedicalRecord record = MedicalRecord.openForAppointment(PATIENT, DOCTOR, DEPARTMENT,
            LocalDate.of(2026, 9, 21), "cough", APPOINTMENT_ID, NOW);
    private final Appointment appointment = Appointment.restore(APPOINTMENT_ID, PATIENT, DOCTOR, DEPARTMENT,
            LocalDate.of(2026, 9, 21), LocalTime.of(9, 0), AppointmentStatus.IN_EXAM, "cough",
            NOW.minusSeconds(600), NOW, (short) 1, UUID.randomUUID(), NOW.minusSeconds(300),
            null, "OUTPATIENT_EXAM", NOW.minusSeconds(300), NOW.minusSeconds(120), null);

    @BeforeEach
    void setUp() {
        when(records.findByIdForUpdate(record.getRecordId())).thenReturn(Optional.of(record));
        when(appointments.findByIdForUpdate(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));
        when(actors.current()).thenReturn(new ActorIdentity(DOCTOR, "DOCTOR"));
        when(staff.departmentOf(DOCTOR)).thenReturn(Optional.of(DEPARTMENT));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void create_persistsGeneratedAuthorityFromExactOpenInExamVisit() {
        var service = service(true);

        var created = service.create(record.getRecordId(), new CreateClinicalLabOrderRequest(
                "CBC", LocalDate.of(2026, 9, 21), "LAB-CBC"));

        assertThat(created.sourceOrderId()).isNotNull();
        assertThat(created.recordId()).isEqualTo(record.getRecordId());
        assertThat(created.patientId()).isEqualTo(PATIENT);
        assertThat(created.requestingDoctorId()).isEqualTo(DOCTOR);
        assertThat(created.requestingDepartmentId()).isEqualTo(DEPARTMENT);
        assertThat(created.careEpisodeId()).isEqualTo(APPOINTMENT_ID);
        assertThat(created.careEpisodeType().name()).isEqualTo("OUTPATIENT_VISIT");
        assertThat(created.priceCode()).isEqualTo("LAB-CBC");
        assertThat(created.orderedAt()).isEqualTo(NOW);
        verify(orders).save(any(ClinicalLabOrder.class));
    }

    @Test
    void create_preservesFutureScheduledLabDate() {
        var service = service(true);

        var created = service.create(record.getRecordId(), new CreateClinicalLabOrderRequest(
                "CBC", LocalDate.of(2026, 9, 22), "LAB-CBC"));

        assertThat(created.requestedDate()).isEqualTo(LocalDate.of(2026, 9, 22));
        verify(orders).save(any(ClinicalLabOrder.class));
    }

    @Test
    void create_rejectsBlankPriceBeforePersisting() {
        var service = service(true);

        assertThatThrownBy(() -> service.create(record.getRecordId(), new CreateClinicalLabOrderRequest(
                "CBC", LocalDate.of(2026, 9, 21), " ")))
                .isInstanceOf(InvalidClinicalDataException.class);
        verify(orders, never()).save(any());
    }

    @Test
    void create_rejectsAStaffActorThatDoesNotMatchTheAssignedDoctor() {
        when(actors.current()).thenReturn(new ActorIdentity(UUID.randomUUID(), "DOCTOR"));
        var service = service(true);

        assertThatThrownBy(() -> service.create(record.getRecordId(), new CreateClinicalLabOrderRequest(
                "CBC", LocalDate.of(2026, 9, 21), "LAB-CBC")))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(orders, never()).save(any());
    }

    @Test
    void create_rejectsRecordOutsideMatchingActiveAppointment() {
        Appointment completed = Appointment.restore(APPOINTMENT_ID, PATIENT, DOCTOR, DEPARTMENT,
                LocalDate.of(2026, 9, 21), LocalTime.of(9, 0), AppointmentStatus.COMPLETED, "cough",
                NOW.minusSeconds(600), NOW, (short) 1, UUID.randomUUID(), NOW.minusSeconds(300),
                null, "OUTPATIENT_EXAM", NOW.minusSeconds(300), NOW.minusSeconds(120), NOW);
        when(appointments.findByIdForUpdate(APPOINTMENT_ID)).thenReturn(Optional.of(completed));
        var service = service(true);

        assertThatThrownBy(() -> service.create(record.getRecordId(), new CreateClinicalLabOrderRequest(
                "CBC", LocalDate.of(2026, 9, 21), "LAB-CBC")))
                .isInstanceOf(InvalidClinicalDataException.class);
        verify(orders, never()).save(any());
    }

    @Test
    void create_isDisabledOutsideOutpatientMvpMode() {
        assertThatThrownBy(() -> service(false, true).create(record.getRecordId(), new CreateClinicalLabOrderRequest(
                "CBC", LocalDate.of(2026, 9, 21), "LAB-CBC")))
                .isInstanceOf(InvalidClinicalDataException.class);
        verify(orders, never()).save(any());
    }

    @Test
    void create_isDisabledWhenCareFinanceGateIsOffEvenIfOutpatientGateIsOn() {
        assertThatThrownBy(() -> service(true, false).create(record.getRecordId(),
                new CreateClinicalLabOrderRequest("CBC", LocalDate.of(2026, 9, 21), "LAB-CBC")))
                .isInstanceOf(InvalidClinicalDataException.class);
        verify(records, never()).findByIdForUpdate(any());
        verify(orders, never()).save(any());
    }

    private ClinicalLabOrderApplicationService service(boolean outpatientEnabled) {
        return service(outpatientEnabled, true);
    }

    private ClinicalLabOrderApplicationService service(boolean outpatientEnabled, boolean careFinanceEnabled) {
        return new ClinicalLabOrderApplicationService(appointments, records, orders, actors, staff,
                new OutpatientMvpMode(outpatientEnabled), careFinanceEnabled, clock);
    }
}
