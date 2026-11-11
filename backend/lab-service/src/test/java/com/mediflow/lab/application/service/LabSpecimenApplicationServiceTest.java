package com.mediflow.lab.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mediflow.lab.application.dto.request.LabSpecimenRequests.CollectSpecimenRequest;
import com.mediflow.lab.application.dto.request.LabSpecimenRequests.SpecimenDecisionRequest;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContext;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContextPort;
import com.mediflow.lab.application.port.out.LabSpecimenRepositoryPort;
import com.mediflow.lab.application.port.out.LabTestRepositoryPort;
import com.mediflow.lab.domain.exception.LabRuleException;
import com.mediflow.lab.domain.model.CareEpisodeType;
import com.mediflow.lab.domain.model.LabSpecimen;
import com.mediflow.lab.domain.model.LabSpecimenDecision;
import com.mediflow.lab.domain.model.LabTest;

class LabSpecimenApplicationServiceTest {
    static final Instant AT = Instant.parse("2026-11-11T04:00:00Z");
    final LabTestRepositoryPort tests = mock(LabTestRepositoryPort.class);
    final LabSpecimenRepositoryPort specimens = mock(LabSpecimenRepositoryPort.class);
    final AuthenticatedStaffContextPort staff = mock(AuthenticatedStaffContextPort.class);
    final UUID patient = UUID.randomUUID();
    final UUID nurse = UUID.randomUUID();
    final LabSpecimenApplicationService service = new LabSpecimenApplicationService(tests, specimens, staff,
            Clock.fixed(AT, ZoneOffset.UTC), true);

    LabTest test(UUID patientId) {
        LabTest test = LabTest.createV2(UUID.randomUUID(), patientId, UUID.randomUUID(), UUID.randomUUID(),
                CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), "Glucose", "LAB-GLU", LocalDate.of(2026, 11, 11));
        when(tests.findById(test.getTestId())).thenReturn(Optional.of(test));
        return test;
    }

    void signedIn() {
        when(staff.currentStaff()).thenReturn(Optional.of(new AuthenticatedStaffContext(nurse, "NURSE")));
    }

    @Test void oneSpecimenServesSeveralTestsOfTheSamePatient() {
        signedIn();
        LabTest first = test(patient);
        LabTest second = test(patient);
        var dto = service.collect(first.getTestId(), new CollectSpecimenRequest("BLOOD", List.of(second.getTestId()), null));
        ArgumentCaptor<LabSpecimen> saved = ArgumentCaptor.forClass(LabSpecimen.class);
        verify(specimens).insert(saved.capture());
        assertThat(saved.getValue().getTestIds()).containsExactly(first.getTestId(), second.getTestId());
        assertThat(dto.collectedBy()).isEqualTo(nurse);
        verify(tests, never()).save(any());
    }

    @Test void cancelledTestsCannotReceiveASpecimen() {
        signedIn();
        LabTest active = test(patient);
        LabTest cancelled = test(patient);
        cancelled.cancel("Bác sĩ hủy chỉ định", UUID.randomUUID(), AT);
        assertThatThrownBy(() -> service.collect(cancelled.getTestId(), new CollectSpecimenRequest("BLOOD", List.of(), null)))
                .isInstanceOfSatisfying(LabRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("LAB_SPECIMEN_TEST_NOT_ACTIVE"));
        assertThatThrownBy(() -> service.collect(active.getTestId(),
                new CollectSpecimenRequest("BLOOD", List.of(cancelled.getTestId()), null)))
                .isInstanceOfSatisfying(LabRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("LAB_SPECIMEN_TEST_NOT_ACTIVE"));
        verify(specimens, never()).insert(any());
    }

    @Test void testsOfAnotherPatientAreRejected() {
        signedIn();
        LabTest first = test(patient);
        LabTest other = test(UUID.randomUUID());
        assertThatThrownBy(() -> service.collect(first.getTestId(),
                new CollectSpecimenRequest("BLOOD", List.of(other.getTestId()), null)))
                .isInstanceOfSatisfying(LabRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("LAB_SPECIMEN_PATIENT_MISMATCH"));
        verify(specimens, never()).insert(any());
    }

    @Test void recollectionLinksTheRejectedSpecimenWithoutNewTests() {
        signedIn();
        LabTest first = test(patient);
        LabSpecimen rejected = LabSpecimen.collect(patient, "BLOOD", List.of(first.getTestId()), null, nurse, AT);
        rejected.decide(LabSpecimenDecision.REJECTED, "Tan huyết", UUID.randomUUID(), AT);
        when(specimens.findById(rejected.getSpecimenId())).thenReturn(Optional.of(rejected));

        service.collect(first.getTestId(), new CollectSpecimenRequest("BLOOD", List.of(), rejected.getSpecimenId()));

        ArgumentCaptor<LabSpecimen> saved = ArgumentCaptor.forClass(LabSpecimen.class);
        verify(specimens).insert(saved.capture());
        assertThat(saved.getValue().getReplacesSpecimenId()).isEqualTo(rejected.getSpecimenId());
        assertThat(saved.getValue().getTestIds()).containsExactly(first.getTestId());
        verify(tests, never()).save(any());
    }

    @Test void onlyARejectedSpecimenCanBeReplaced() {
        signedIn();
        LabTest first = test(patient);
        LabSpecimen pending = LabSpecimen.collect(patient, "BLOOD", List.of(first.getTestId()), null, nurse, AT);
        when(specimens.findById(pending.getSpecimenId())).thenReturn(Optional.of(pending));
        assertThatThrownBy(() -> service.collect(first.getTestId(),
                new CollectSpecimenRequest("BLOOD", List.of(), pending.getSpecimenId())))
                .isInstanceOfSatisfying(LabRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("LAB_SPECIMEN_NOT_REJECTED"));
    }

    @Test void rejectionWithoutReasonIsUnprocessable() {
        signedIn();
        LabSpecimen specimen = LabSpecimen.collect(patient, "BLOOD", List.of(UUID.randomUUID()), null, nurse, AT);
        when(specimens.findByIdForUpdate(specimen.getSpecimenId())).thenReturn(Optional.of(specimen));
        assertThatThrownBy(() -> service.decide(specimen.getSpecimenId(), new SpecimenDecisionRequest("REJECTED", null)))
                .isInstanceOf(LabRuleException.class);
        verify(specimens, never()).update(any());
    }
}
