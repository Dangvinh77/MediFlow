package com.mediflow.organization.infrastructure.identity;

import com.mediflow.organization.application.port.out.PatientAccountIdentityPort;
import com.mediflow.organization.domain.exception.InvalidAccountException;
import com.mediflow.patient.application.dto.response.PatientDTO;
import com.mediflow.patient.application.port.in.GetPatientUseCase;
import com.mediflow.patient.application.port.in.UpdatePatientUseCase;
import com.mediflow.patient.application.port.in.ReadPatientIdentityUseCase;
import com.mediflow.patient.domain.model.Gender;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PatientAccountIdentityAdapterTest {
    private final GetPatientUseCase patients = mock(GetPatientUseCase.class);
    private final ReadPatientIdentityUseCase patientIdentity = mock(ReadPatientIdentityUseCase.class);
    private final PatientAccountIdentityPort identity = new PatientAccountIdentityAdapter(
            patients, mock(UpdatePatientUseCase.class), patientIdentity);

    @Test
    void permitsEmailChangeForAnActivePatientProfile() {
        UUID patientId = UUID.randomUUID();
        when(patients.getById(patientId)).thenReturn(patient(patientId, false));

        assertThatCode(() -> identity.requireActivePatient(patientId)).doesNotThrowAnyException();
        verify(patients).getById(patientId);
    }

    @Test
    void rejectsEmailChangeForAnArchivedPatientProfile() {
        UUID patientId = UUID.randomUUID();
        when(patients.getById(patientId)).thenReturn(patient(patientId, true));

        assertThatThrownBy(() -> identity.requireActivePatient(patientId))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessageContaining("archived patient profile");
        verify(patients).getById(patientId);
    }

    private static PatientDTO patient(UUID patientId, boolean archived) {
        return new PatientDTO(patientId, "Patient", LocalDate.of(1990, 1, 1), Gender.M,
                "001", "Address", "0900000000", null, null, null, null, archived);
    }
}
