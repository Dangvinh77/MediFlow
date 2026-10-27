package com.mediflow.lab.application.service;
import com.mediflow.lab.application.port.out.LabTestRepositoryPort;
import com.mediflow.lab.application.port.out.CurrentPatientIdentityPort;
import com.mediflow.lab.application.mapper.LabTestDtoMapper;
import com.mediflow.lab.domain.model.LabTest;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.exception.ForbiddenOperationException;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class PatientResultsServiceTest {
    final LabTestRepositoryPort repository = mock(LabTestRepositoryPort.class);
    final CurrentPatientIdentityPort actors = mock(CurrentPatientIdentityPort.class);
    final LabTestDtoMapper mapper = mock(LabTestDtoMapper.class);
    final UUID patient = UUID.randomUUID();
    @Test void derivesScopeFromVerifiedIdentityAndPagesCompletedResults() {
        when(actors.patientId()).thenReturn(patient);
        var page = PageQuery.of(1, 5);
        when(repository.findCompletedByPatient(patient, page)).thenReturn(PageResult.of(List.of(), 7, 1, 5));
        var result = new PatientResultsService(repository, actors, mapper).mine(page);
        assertThat(result.number()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(7);
        verify(repository).findCompletedByPatient(patient, page);
        verify(repository, never()).findByPatient(any());
    }
    @Test void missingPatientMappingFailsClosedBeforeQuery() {
        when(actors.patientId()).thenReturn(null);
        assertThatThrownBy(() -> new PatientResultsService(repository, actors, mapper).mine(PageQuery.of(0, 20)))
            .isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(repository);
    }
}
