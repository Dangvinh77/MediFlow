package com.mediflow.patient.application.service;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.patient.application.dto.request.CreatePatientRequest;
import com.mediflow.patient.application.dto.request.UpdatePatientRequest;
import com.mediflow.patient.application.dto.response.PatientDTO;
import com.mediflow.patient.application.mapper.PatientDtoMapper;
import com.mediflow.patient.application.port.out.PatientEventPublisherPort;
import com.mediflow.patient.application.port.out.PatientRepositoryPort;
import com.mediflow.patient.domain.exception.PatientNotFoundException;
import com.mediflow.patient.domain.model.GioiTinh;
import com.mediflow.patient.domain.model.Patient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Application unit tests.
 *
 * <p>The mocks are the <strong>out-ports</strong>, never {@code PatientJpaRepository}. Mocking the
 * JPA repository here would test the wrong seam and couple this test to the persistence
 * technology — see docs/ai/09-testing.md.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PatientApplicationServiceTest {

    @Mock
    PatientRepositoryPort repository;
    @Mock
    PatientEventPublisherPort events;
    @Mock
    PatientDtoMapper mapper;

    @InjectMocks
    PatientApplicationService service;

    private static CreatePatientRequest yeuCauHopLe() {
        return new CreatePatientRequest("Nguyễn Văn A", LocalDate.of(1990, 1, 1), GioiTinh.M,
                "012345678", "Hà Nội", "0901234567", "a@example.com", null);
    }

    private static Patient daLuu(UUID id) {
        return Patient.khoiPhuc(id, "Nguyễn Văn A", LocalDate.of(1990, 1, 1), GioiTinh.M,
                "012345678", "Hà Nội", "0901234567", "a@example.com", null, null, null);
    }

    // BR-P1
    @Test
    void create_duplicateCmnd_throwsDuplicateResource() {
        when(repository.existsBySoCmnd("012345678")).thenReturn(true);

        assertThatThrownBy(() -> service.create(yeuCauHopLe()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("012345678");

        verify(repository, never()).save(any());
    }

    // BR-P8 — a rejected create must not announce anything
    @Test
    void create_duplicateCmnd_publishesNothing() {
        when(repository.existsBySoCmnd("012345678")).thenReturn(true);

        assertThatThrownBy(() -> service.create(yeuCauHopLe()))
                .isInstanceOf(DuplicateResourceException.class);

        verify(events, never()).publishCreated(any());
    }

    // BR-P7
    @Test
    void create_valid_savesAndPublishesPatientCreated() {
        UUID id = UUID.randomUUID();
        when(repository.existsBySoCmnd("012345678")).thenReturn(false);
        when(repository.save(any(Patient.class))).thenReturn(daLuu(id));

        service.create(yeuCauHopLe());

        ArgumentCaptor<Patient> captor = ArgumentCaptor.forClass(Patient.class);
        verify(events).publishCreated(captor.capture());
        assertThat(captor.getValue().getMaBenhNhan()).isEqualTo(id);
    }

    /** The aggregate must be built through the factory, so its invariants always run. */
    @Test
    void create_buildsDomainModelThroughFactory() {
        when(repository.existsBySoCmnd("012345678")).thenReturn(false);
        when(repository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(yeuCauHopLe());

        ArgumentCaptor<Patient> captor = ArgumentCaptor.forClass(Patient.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getHoTen()).isEqualTo("Nguyễn Văn A");
        assertThat(captor.getValue().getMaBenhNhan()).isNull();   // id do persistence sinh
    }

    @Test
    void getById_notFound_throwsPatientNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(PatientNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void update_notFound_throwsAndPublishesNothing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, new UpdatePatientRequest(
                "Tên Mới", LocalDate.of(1990, 1, 1), GioiTinh.F, null, null, null, null)))
                .isInstanceOf(PatientNotFoundException.class);

        verify(events, never()).publishUpdated(any());
    }

    @Test
    void update_valid_publishesPatientUpdated() {
        UUID id = UUID.randomUUID();
        Patient p = daLuu(id);
        when(repository.findById(id)).thenReturn(Optional.of(p));
        when(repository.save(any(Patient.class))).thenReturn(p);

        service.update(id, new UpdatePatientRequest("Tên Mới", LocalDate.of(1990, 1, 1),
                GioiTinh.F, null, null, null, null));

        verify(events).publishUpdated(any(Patient.class));
    }

    /** A blank keyword means "no filter" — it must reach the port as null, not as "". */
    @Test
    void search_blankKeyword_passesNullToPort() {
        when(repository.search(isNull(), any(PageQuery.class)))
                .thenReturn(PageResult.of(List.of(), 0, 0, 20));

        service.search("   ", PageQuery.of(0, 20));

        verify(repository).search(isNull(), any(PageQuery.class));
    }

    @Test
    void search_trimsKeyword() {
        when(repository.search(any(), any(PageQuery.class)))
                .thenReturn(PageResult.of(List.of(), 0, 0, 20));

        service.search("  Nguyễn  ", PageQuery.of(0, 20));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(repository).search(captor.capture(), any(PageQuery.class));
        assertThat(captor.getValue()).isEqualTo("Nguyễn");
    }

    @Test
    void delete_notFound_throwsAndDoesNotDelete() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(PatientNotFoundException.class);

        verify(repository, never()).deleteById(any());
    }

    @Test
    void delete_found_deletes() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(daLuu(id)));

        service.delete(id);

        verify(repository).deleteById(id);
    }

    @Test
    void search_mapsDomainToDto() {
        UUID id = UUID.randomUUID();
        PatientDTO dto = new PatientDTO(id, "Nguyễn Văn A", LocalDate.of(1990, 1, 1), GioiTinh.M,
                "012345678", "Hà Nội", "0901234567", "a@example.com", null, null, null);
        when(repository.search(any(), any(PageQuery.class)))
                .thenReturn(PageResult.of(List.of(daLuu(id)), 1, 0, 20));
        when(mapper.toDto(any(Patient.class))).thenReturn(dto);

        PageResult<PatientDTO> result = service.search(null, PageQuery.of(0, 20));

        assertThat(result.content()).containsExactly(dto);
        assertThat(result.totalElements()).isEqualTo(1);
    }
}
