package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.response.OpenVisitDTO;
import com.mediflow.clinical.application.dto.response.ReturnSourceDTO;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.MedicalRecordStatus;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.OutpatientRegistrationRepositoryPort;
import com.mediflow.common.exception.ForbiddenOperationException;
import org.junit.jupiter.api.Test;

class OpenVisitApplicationServiceTest {
    private final OutpatientRegistrationRepositoryPort registrations = mock(OutpatientRegistrationRepositoryPort.class);
    private final CurrentClinicalActorPort actors = mock(CurrentClinicalActorPort.class);
    private final MedicalRecordRepositoryPort records = mock(MedicalRecordRepositoryPort.class);
    private final OpenVisitApplicationService service = new OpenVisitApplicationService(registrations, actors, records);
    private final UUID doctor = UUID.randomUUID();
    private final OpenVisitDTO visit = new OpenVisitDTO(UUID.randomUUID(), UUID.randomUUID(), doctor, UUID.randomUUID(),
            LocalDate.of(2026, 11, 10), Instant.parse("2026-11-10T01:00:00Z"));

    private static MedicalRecord record(MedicalRecordStatus status, LocalDate date) {
        MedicalRecord record = mock(MedicalRecord.class);
        when(record.getRecordId()).thenReturn(UUID.randomUUID());
        when(record.getStatus()).thenReturn(status);
        when(record.getExaminationDate()).thenReturn(date);
        when(record.getDepartmentId()).thenReturn(UUID.randomUUID());
        return record;
    }

    @Test void returnSourcesAreFinishedRecordsNewestFirst() {
        UUID patient = UUID.randomUUID();
        MedicalRecord open = record(MedicalRecordStatus.OPEN, LocalDate.of(2026, 11, 10));
        MedicalRecord completed = record(MedicalRecordStatus.COMPLETED, LocalDate.of(2026, 11, 1));
        MedicalRecord closed = record(MedicalRecordStatus.CLOSED_WITHOUT_CONCLUSION, LocalDate.of(2026, 11, 5));
        when(records.findByPatient(patient)).thenReturn(List.of(open, completed, closed));
        assertThat(service.returnSources(patient)).containsExactly(
                new ReturnSourceDTO(closed.getRecordId(), "CLOSED_WITHOUT_CONCLUSION", LocalDate.of(2026, 11, 5), closed.getDepartmentId()),
                new ReturnSourceDTO(completed.getRecordId(), "COMPLETED", LocalDate.of(2026, 11, 1), completed.getDepartmentId()));
    }

    @Test void doctorListsOwnOpenVisitsOldestFirst() {
        when(actors.current()).thenReturn(new ActorIdentity(UUID.randomUUID(), doctor, null, "DOCTOR"));
        when(registrations.findOpenVisitsByDoctor(doctor, 100)).thenReturn(List.of(visit));
        assertThat(service.forCurrentDoctor()).containsExactly(visit);
    }

    @Test void accountWithoutStaffIdentityHasNoOwnList() {
        when(actors.current()).thenReturn(new ActorIdentity(UUID.randomUUID(), null, null, "DOCTOR"));
        assertThatThrownBy(service::forCurrentDoctor).isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(registrations);
    }

    @Test void patientListHasNoClinicalContent() {
        UUID patient = UUID.randomUUID();
        when(registrations.findOpenVisitsByPatient(patient)).thenReturn(List.of(visit));
        assertThat(service.forPatient(patient)).containsExactly(visit);
        assertThat(Arrays.stream(OpenVisitDTO.class.getRecordComponents()).map(component -> component.getName()))
                .containsExactly("registrationId", "recordId", "doctorId", "departmentId", "sessionDate", "startedAt");
    }
}
