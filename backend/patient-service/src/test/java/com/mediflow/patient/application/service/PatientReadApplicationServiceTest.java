package com.mediflow.patient.application.service;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.patient.application.mapper.PatientDtoMapper;
import com.mediflow.patient.application.port.out.PatientRepositoryPort;
import com.mediflow.patient.domain.exception.PatientNotFoundException;
import com.mediflow.patient.domain.model.Gender;
import com.mediflow.patient.domain.model.Patient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PatientReadApplicationServiceTest {
    private final PatientRepositoryPort repository = mock(PatientRepositoryPort.class);
    private final PatientReadApplicationService service = new PatientReadApplicationService(repository, new PatientDtoMapper());
    private final UUID id = UUID.randomUUID();

    @Test
    void getByIdMapsVietnamesePublicContract() {
        when(repository.findById(id)).thenReturn(Optional.of(patient()));
        var dto = service.getById(id);
        assertThat(dto.maBenhNhan()).isEqualTo(id);
        assertThat(dto.hoTen()).isEqualTo("Nguyen Van A");
        assertThat(dto.gioiTinh()).isEqualTo(Gender.M);
    }

    @Test
    void missingPatientIsNotFound() {
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(id)).isInstanceOf(PatientNotFoundException.class);
    }

    @Test
    void searchTrimsKeywordAndMapsPage() {
        when(repository.search("nguyen", new PageQuery(0, 20)))
                .thenReturn(PageResult.of(List.of(patient()), 1, 0, 20));
        var result = service.search("  nguyen  ", PageQuery.of(null, null));
        assertThat(result.content()).hasSize(1);
        verify(repository).search("nguyen", new PageQuery(0, 20));
    }

    @Test
    void existsReturnsFalseWithoutTurningAbsenceIntoError() {
        when(repository.existsById(id)).thenReturn(false);
        assertThat(service.exists(id).exists()).isFalse();
        assertThat(service.exists(id).patientId()).isEqualTo(id);
    }

    private Patient patient() {
        return Patient.restore(id, "Nguyen Van A", LocalDate.of(1990, 1, 1), Gender.M,
                "001", "Hanoi", "0900000000", "a@example.com", "HI-1",
                Instant.parse("2026-01-01T00:00:00Z"), null);
    }
}
