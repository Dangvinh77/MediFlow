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
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.OutpatientRegistrationRepositoryPort;
import com.mediflow.common.exception.ForbiddenOperationException;
import org.junit.jupiter.api.Test;

class OpenVisitApplicationServiceTest {
    private final OutpatientRegistrationRepositoryPort registrations = mock(OutpatientRegistrationRepositoryPort.class);
    private final CurrentClinicalActorPort actors = mock(CurrentClinicalActorPort.class);
    private final OpenVisitApplicationService service = new OpenVisitApplicationService(registrations, actors);
    private final UUID doctor = UUID.randomUUID();
    private final OpenVisitDTO visit = new OpenVisitDTO(UUID.randomUUID(), UUID.randomUUID(), doctor, UUID.randomUUID(),
            LocalDate.of(2026, 11, 10), Instant.parse("2026-11-10T01:00:00Z"));

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
