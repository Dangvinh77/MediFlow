package com.mediflow.clinical.application.service;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.mapper.ClinicalDtoMapper;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.exception.ForbiddenOperationException;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class PatientResultsServiceTest {
    final MedicalRecordRepositoryPort repository = mock(MedicalRecordRepositoryPort.class);
    final CurrentClinicalActorPort actors = mock(CurrentClinicalActorPort.class);
    final ClinicalDtoMapper mapper = mock(ClinicalDtoMapper.class);
    final UUID patient = UUID.randomUUID();
    @Test void derivesScopeFromVerifiedIdentityAndPagesCompletedResults() {
        when(actors.current()).thenReturn(new com.mediflow.clinical.application.dto.command.ActorIdentity(UUID.randomUUID(), null, patient, "PATIENT"));
        var page = PageQuery.of(1, 5);
        when(repository.findCompletedByPatient(patient, page)).thenReturn(PageResult.of(List.of(), 7, 1, 5));
        var result = new PatientResultsService(repository, actors, mapper).mine(page);
        assertThat(result.number()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(7);
        verify(repository).findCompletedByPatient(patient, page);
        verify(repository, never()).findByPatient(any());
    }
    @Test void missingPatientMappingFailsClosedBeforeQuery() {
        when(actors.current()).thenReturn(new com.mediflow.clinical.application.dto.command.ActorIdentity(UUID.randomUUID(), null, null, "PATIENT"));
        assertThatThrownBy(() -> new PatientResultsService(repository, actors, mapper).mine(PageQuery.of(0, 20)))
            .isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(repository);
    }
}
