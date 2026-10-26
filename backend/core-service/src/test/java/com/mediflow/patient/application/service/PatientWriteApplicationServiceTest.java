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
    private final com.mediflow.patient.application.port.out.PatientIdentityAuditPort audit = mock(com.mediflow.patient.application.port.out.PatientIdentityAuditPort.class);
    private final PatientWriteApplicationService service = new PatientWriteApplicationService(
            repository, new PatientDtoMapper(), events, correlationIds, audit);
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
        when(repository.findByIdForUpdate(patient.patientId())).thenReturn(Optional.of(patient));

        var dto = service.update(patient.patientId(), new UpdatePatientRequest(
                "Nguyen Van B", LocalDate.of(1991, 1, 1), Gender.F,
                "Da Nang", "0900000001", "b@example.com", null, null, "Correct birth date"));

        assertThat(dto.soCmnd()).isEqualTo("001");
        assertThat(dto.hoTen()).isEqualTo("Nguyen Van B");
        verify(events).publishUpdated(any());
    }

    @Test
    void correctsIdentityAndAllowsKeepingTheSameNumber() {
        Patient patient = Patient.create("A", LocalDate.of(1990, 1, 1), Gender.M,
                "001", null, null, null, null);
        when(repository.findByIdForUpdate(patient.patientId())).thenReturn(Optional.of(patient));
        var createdAt = patient.createdAt();
        var request = new UpdatePatientRequest("A", patient.dateOfBirth(), Gender.M,
                null, null, null, null, " 002 ", "Correct identity");

        var corrected = service.update(patient.patientId(), request);
        var unchanged = service.update(patient.patientId(), request);

        assertThat(corrected.soCmnd()).isEqualTo("002");
        assertThat(unchanged.soCmnd()).isEqualTo("002");
        assertThat(corrected.maBenhNhan()).isEqualTo(patient.patientId());
        assertThat(patient.createdAt()).isEqualTo(createdAt);
        verify(repository, times(2)).existsByIdentityNumberAndPatientIdNot("002", patient.patientId());
        verify(events, times(2)).publishUpdated(any());
    }

    @Test
    void duplicateCorrectionDoesNotMutateOrSaveOrPublish() {
        Patient patient = Patient.create("A", LocalDate.of(1990, 1, 1), Gender.M,
                "001", null, null, null, null);
        when(repository.findByIdForUpdate(patient.patientId())).thenReturn(Optional.of(patient));
        when(repository.existsByIdentityNumberAndPatientIdNot("002", patient.patientId())).thenReturn(true);

        assertThatThrownBy(() -> service.update(patient.patientId(), new UpdatePatientRequest(
                "B", patient.dateOfBirth(), Gender.M, null, null, null, null, " 002 ", "Correct identity")))
                .isInstanceOf(com.mediflow.common.exception.DuplicateResourceException.class);
        assertThat(patient.identityNumber()).isEqualTo("001");
        assertThat(patient.fullName()).isEqualTo("A");
        verify(repository, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void deleteMissingPatientThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(PatientNotFoundException.class);
        verify(repository, never()).deleteById(any());
    }

    @Test
    void archiveRetainsPatientIdentityAndNeverPhysicallyDeletes() {
        Patient patient = Patient.create("A", LocalDate.of(1990, 1, 1), Gender.M, "001", null, null, null, null);
        when(repository.findByIdForUpdate(patient.patientId())).thenReturn(Optional.of(patient));
        service.delete(patient.patientId());
        service.delete(patient.patientId());
        verify(events).publishArchived(any());
        assertThat(patient.archived()).isTrue();
        assertThat(patient.identityNumber()).isEqualTo("001");
        verify(repository).save(patient);
        verify(repository, never()).deleteById(any());
        assertThatThrownBy(() -> service.update(patient.patientId(), new UpdatePatientRequest(
                "B", patient.dateOfBirth(), Gender.M, null, null, null, null, null)))
                .hasMessageContaining("lưu trữ");
    }

    @Test
    void identityCorrectionRequiresReasonAndAuditsOnlyActualChanges() {
        Patient patient = Patient.create("A", LocalDate.of(1990, 1, 1), Gender.M, "001", null, null, null, null);
        when(repository.findByIdForUpdate(patient.patientId())).thenReturn(Optional.of(patient));
        assertThatThrownBy(() -> service.update(patient.patientId(), new UpdatePatientRequest(
                "A", patient.dateOfBirth(), Gender.M, null, null, null, null, "002")))
                .hasMessageContaining("lý do");
        verify(repository, never()).save(any());
        verifyNoInteractions(audit, events);
        var request = new UpdatePatientRequest("A", patient.dateOfBirth(), Gender.M,
                null, null, null, null, "002", "  Verified document  ");
        service.update(patient.patientId(), request);
        service.update(patient.patientId(), request);
        verify(audit).record(eq(patient.patientId()), eq("Verified document"), eq("001"), eq("002"),
                eq(patient.dateOfBirth()), eq(patient.dateOfBirth()), any(), eq(correlationId));
        verifyNoMoreInteractions(audit);
    }

    private CreatePatientRequest createRequest(String identityNumber) {
        return new CreatePatientRequest("Nguyen Van A", LocalDate.of(1990, 1, 1), Gender.M,
                identityNumber, "Hanoi", "0900000000", "a@example.com", null);
    }
}
