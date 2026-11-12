package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.request.AddAllergyRequest;
import com.mediflow.clinical.application.dto.request.AllergyStatusRequest;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.PatientAllergyRepositoryPort;
import com.mediflow.clinical.domain.model.AllergySeverity;
import com.mediflow.clinical.domain.model.AllergyStatus;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.PatientAllergy;
import com.mediflow.common.exception.ForbiddenOperationException;

class PatientAllergyApplicationServiceTest {
    static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");
    final UUID patient = UUID.randomUUID();
    final PatientAllergyRepositoryPort allergies = mock(PatientAllergyRepositoryPort.class);
    final MedicalRecordRepositoryPort records = mock(MedicalRecordRepositoryPort.class);
    final CurrentClinicalActorPort actors = mock(CurrentClinicalActorPort.class);
    final RecordReadGuard guard = mock(RecordReadGuard.class);
    final IdempotentCommandPort idempotency = mock(IdempotentCommandPort.class);
    final PatientAllergyApplicationService service = new PatientAllergyApplicationService(allergies, records, actors,
            guard, true, idempotency, Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")));
    final UUID staffId = UUID.randomUUID();

    @BeforeEach void setUp() {
        as("DOCTOR");
        lenient().when(allergies.findActiveByKey(any(), anyString())).thenReturn(Optional.empty());
        lenient().when(idempotency.execute(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyInt(),
                any(), any())).thenAnswer(call -> new IdempotentCommandPort.Result<>(
                        ((Supplier<?>) call.getArgument(8)).get(), call.getArgument(6), null, false));
    }

    private void as(String role) {
        lenient().when(actors.current()).thenReturn(new ActorIdentity(UUID.randomUUID(), staffId, null, role));
    }

    private AddAllergyRequest penicillin(UUID sourceRecordId) {
        return new AddAllergyRequest("Penicillin", "Nổi mẩn", "MODERATE", sourceRecordId);
    }

    @ParameterizedTest @ValueSource(strings = {"DOCTOR", "NURSE"})
    void nurseAndDoctorAddAllergy(String role) {
        as(role);
        var result = service.add(patient, penicillin(null), "k");
        assertThat(result.status()).isEqualTo(201);
        assertThat(result.data().recordedByStaffId()).isEqualTo(staffId);
        assertThat(result.data().status()).isEqualTo("ACTIVE");
        verify(guard).requirePatient(patient);
        verify(allergies).insert(any());
    }

    @Test void otherRolesCannotAdd() {
        as("ADMIN");
        assertThatThrownBy(() -> service.add(patient, penicillin(null), "k")).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test void sourceRecordMustBelongToPatient() {
        MedicalRecord other = MedicalRecord.openForAppointment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.parse("2026-11-10"), "Sốt", UUID.randomUUID(), NOW);
        when(records.findById(other.getRecordId())).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.add(patient, penicillin(other.getRecordId()), "k"))
                .extracting("code").isEqualTo("ALLERGY_INVALID");
        UUID missing = UUID.randomUUID();
        when(records.findById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.add(patient, penicillin(missing), "k"))
                .extracting("code").isEqualTo("ALLERGY_INVALID");
        verify(allergies, never()).insert(any());
    }

    @Test void unknownSeverityIsInvalid() {
        assertThatThrownBy(() -> service.add(patient, new AddAllergyRequest("Penicillin", null, "DEADLY", null), "k"))
                .extracting("code").isEqualTo("ALLERGY_INVALID");
    }

    @Test void duplicateActiveSubstanceIsRefused() {
        when(allergies.findActiveByKey(patient, PatientAllergy.key("penicillin"))).thenReturn(Optional.of(
                PatientAllergy.record(patient, "penicillin", null, AllergySeverity.MILD, null, staffId, NOW)));
        assertThatThrownBy(() -> service.add(patient, new AddAllergyRequest("  PENICILLIN ", null, null, null), "k"))
                .extracting("code").isEqualTo("ALLERGY_INVALID");
    }

    @Test void onlyDoctorChangesStatus() {
        PatientAllergy allergy = PatientAllergy.record(patient, "Penicillin", null, AllergySeverity.MILD, null, staffId, NOW);
        when(allergies.findByIdForUpdate(allergy.getAllergyId())).thenReturn(Optional.of(allergy));
        as("NURSE");
        assertThatThrownBy(() -> service.changeStatus(patient, allergy.getAllergyId(),
                new AllergyStatusRequest("RESOLVED", "Đã giải mẫn cảm"), "k")).isInstanceOf(ForbiddenOperationException.class);
        as("DOCTOR");
        var result = service.changeStatus(patient, allergy.getAllergyId(), new AllergyStatusRequest("RESOLVED", "Đã giải mẫn cảm"), "k");
        assertThat(result.data().status()).isEqualTo("RESOLVED");
        verify(allergies).changeStatus(allergy);
    }

    @Test void allergyOfAnotherPatientIsNotFound() {
        PatientAllergy allergy = PatientAllergy.record(UUID.randomUUID(), "Penicillin", null, AllergySeverity.MILD, null, staffId, NOW);
        when(allergies.findByIdForUpdate(allergy.getAllergyId())).thenReturn(Optional.of(allergy));
        assertThatThrownBy(() -> service.changeStatus(patient, allergy.getAllergyId(),
                new AllergyStatusRequest("RESOLVED", "Lý do"), "k"))
                .isInstanceOf(com.mediflow.common.exception.ResourceNotFoundException.class);
    }

    @Test void allergyIsNeverDeleted() {
        assertThat(Arrays.stream(PatientAllergyRepositoryPort.class.getDeclaredMethods()).map(Method::getName))
                .noneMatch(name -> name.toLowerCase().contains("delete") || name.toLowerCase().contains("remove"));
    }

    @Test void activeListedFirstNewestFirst() {
        PatientAllergy older = PatientAllergy.record(patient, "Aspirin", null, AllergySeverity.MILD, null, staffId, NOW.minusSeconds(120));
        PatientAllergy newer = PatientAllergy.record(patient, "Penicillin", null, AllergySeverity.MILD, null, staffId, NOW);
        PatientAllergy wrong = PatientAllergy.record(patient, "Sữa", null, AllergySeverity.MILD, null, staffId, NOW.minusSeconds(60));
        wrong.changeStatus(AllergyStatus.ENTERED_IN_ERROR, "Nhập nhầm", staffId, NOW);
        when(allergies.findByPatient(patient)).thenReturn(List.of(older, wrong, newer));
        var list = service.list(patient);
        assertThat(list.active()).extracting("substance").containsExactly("Penicillin", "Aspirin");
        assertThat(list.inactive()).extracting("substance").containsExactly("Sữa");
    }

    @Test void nurseOfAnotherDepartmentCannotRead() {
        as("NURSE");
        when(guard.requirePatient(patient)).thenThrow(new ForbiddenOperationException("RECORD_READ_DENIED", "no"));
        assertThatThrownBy(() -> service.list(patient)).isInstanceOf(ForbiddenOperationException.class);
        verify(allergies, never()).findByPatient(any());
    }

    @Test void addReplaysWithSameKey() {
        service.add(patient, penicillin(null), "same");
        service.add(patient, penicillin(null), "same");
        ArgumentCaptor<String> fingerprints = ArgumentCaptor.forClass(String.class);
        verify(idempotency, times(2)).execute(anyString(), eq("POST"), eq("/api/v2/patients/" + patient + "/allergies"),
                eq("same"), fingerprints.capture(), any(), eq(201), any(), any());
        assertThat(fingerprints.getAllValues().get(0)).isEqualTo(fingerprints.getAllValues().get(1));
    }
}
