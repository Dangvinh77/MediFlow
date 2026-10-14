package com.mediflow.patient.application.service;

import com.mediflow.patient.application.dto.request.CreatePatientRequest;
import com.mediflow.patient.application.dto.request.UpdatePatientRequest;
import com.mediflow.patient.application.mapper.PatientDtoMapper;
import com.mediflow.patient.application.port.out.CorrelationIdProvider;
import com.mediflow.patient.application.port.out.PatientEventPublisherPort;
import com.mediflow.patient.application.port.out.PatientRepositoryPort;
import com.mediflow.patient.domain.exception.PatientNotFoundException;
import com.mediflow.patient.domain.model.Gender;
import com.mediflow.patient.domain.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PatientWriteApplicationServiceTest {
    private final PatientRepositoryPort repository = mock(PatientRepositoryPort.class);
    private final PatientEventPublisherPort events = mock(PatientEventPublisherPort.class);
    private final CorrelationIdProvider correlationIds = mock(CorrelationIdProvider.class);
    private final PatientWriteApplicationService service = new PatientWriteApplicationService(
            repository, new PatientDtoMapper(), events, correlationIds);
    private final UUID correlationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(correlationIds.currentOrCreate()).thenReturn(correlationId);
        when(repository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createValidPublishesCreatedEventAndReturnsDto() {
        when(repository.existsByIdentityNumber("001")).thenReturn(false);

        var dto = service.create(createRequest("001"));

        assertThat(dto.maBenhNhan()).isNotNull();
        assertThat(dto.hoTen()).isEqualTo("Nguyen Van A");
        verify(events).publishCreated(any());
    }

    @Test
    void createDuplicateCmndThrowsAndPublishesNothing() {
        when(repository.existsByIdentityNumber("001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest("001")))
                .hasMessageContaining("đã tồn tại");
        verify(repository, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void updatePublishesUpdatedEventAndKeepsIdentityNumber() {
        Patient patient = Patient.create("Nguyen Van A", LocalDate.of(1990, 1, 1), Gender.M,
                "001", "Hanoi", "0900000000", "a@example.com", null);
        when(repository.findById(patient.patientId())).thenReturn(Optional.of(patient));

        var dto = service.update(patient.patientId(), new UpdatePatientRequest(
                "Nguyen Van B", LocalDate.of(1991, 1, 1), Gender.F,
                "Da Nang", "0900000001", "b@example.com", null));

        assertThat(dto.soCmnd()).isEqualTo("001");
        assertThat(dto.hoTen()).isEqualTo("Nguyen Van B");
        verify(events).publishUpdated(any());
    }

    @Test
    void deleteMissingPatientThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(PatientNotFoundException.class);
        verify(repository, never()).deleteById(any());
    }

    private CreatePatientRequest createRequest(String identityNumber) {
        return new CreatePatientRequest("Nguyen Van A", LocalDate.of(1990, 1, 1), Gender.M,
                identityNumber, "Hanoi", "0900000000", "a@example.com", null);
    }
}
