package com.mediflow.inpatient.application.service;

import com.mediflow.inpatient.application.port.out.PlacementAuthorityRepositoryPort;
import com.mediflow.inpatient.domain.model.PlacementAuthoritySnapshot;
import com.mediflow.inpatient.domain.model.enums.AdmissionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LookupPlacementAuthorityServiceTest {
    private static final UUID ADMISSION_ID = UUID.fromString("05000000-0000-0000-0000-000000000001");
    private static final UUID PATIENT_ID = UUID.fromString("06000000-0000-0000-0000-000000000001");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("08000000-0000-0000-0000-000000000001");
    private static final UUID BED_ID = UUID.fromString("09000000-0000-0000-0000-000000000001");
    private static final UUID BED_DEPARTMENT_ID = UUID.fromString("03000000-0000-0000-0000-000000000002");
    private static final Instant ASSIGNED_AT = Instant.parse("2026-10-12T03:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-12T03:00:01Z");
    private final PlacementAuthorityRepositoryPort placements = mock(PlacementAuthorityRepositoryPort.class);
    private final LookupPlacementAuthorityService service = new LookupPlacementAuthorityService(
            placements, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void lookupUsesCurrentBedDepartmentAndIndependentPlacementRevision() {
        when(placements.findByAdmissionId(ADMISSION_ID)).thenReturn(Optional.of(activeSnapshot()));

        var result = service.lookup(ADMISSION_ID);

        assertThat(result.exists()).isTrue();
        assertThat(result.medicationWindowOpen()).isTrue();
        assertThat(result.placementRevision()).isEqualTo(3);
        assertThat(result.placement().departmentId()).isEqualTo(BED_DEPARTMENT_ID);
        assertThat(result.placement().bedId()).isEqualTo(BED_ID);
        assertThat(result.observedAt()).isEqualTo(NOW);
    }

    @Test
    void lookupDistinguishesExistingWithoutPlacementFromMissingAdmission() {
        var snapshot = new PlacementAuthoritySnapshot(ADMISSION_ID, PATIENT_ID,
                AdmissionStatus.ADMITTED, null, null, null, 4,
                null, null, null, null, null);
        when(placements.findByAdmissionId(ADMISSION_ID)).thenReturn(Optional.of(snapshot));
        assertThat(service.lookup(ADMISSION_ID).placement()).isNull();
        assertThat(service.lookup(ADMISSION_ID).exists()).isTrue();

        when(placements.findByAdmissionId(ADMISSION_ID)).thenReturn(Optional.empty());
        var missing = service.lookup(ADMISSION_ID);
        assertThat(missing.exists()).isFalse();
        assertThat(missing.admissionId()).isEqualTo(ADMISSION_ID);
        assertThat(missing.placement()).isNull();
    }

    @Test
    void medicalDischargeClosesWindowEvenWhenPlacementRemainsActive() {
        PlacementAuthoritySnapshot active = activeSnapshot();
        var discharged = new PlacementAuthoritySnapshot(active.admissionId(), active.patientId(),
                AdmissionStatus.MEDICALLY_DISCHARGED, NOW, null, null, active.placementRevision(),
                active.assignmentId(), active.bedId(), active.placementDepartmentId(),
                active.wardCode(), active.assignedAt());
        when(placements.findByAdmissionId(ADMISSION_ID)).thenReturn(Optional.of(discharged));

        assertThat(service.lookup(ADMISSION_ID).medicationWindowOpen()).isFalse();
        assertThat(service.lookup(ADMISSION_ID).placement()).isNotNull();
    }

    private static PlacementAuthoritySnapshot activeSnapshot() {
        return new PlacementAuthoritySnapshot(ADMISSION_ID, PATIENT_ID, AdmissionStatus.ADMITTED,
                null, null, null, 3, ASSIGNMENT_ID, BED_ID, BED_DEPARTMENT_ID,
                "CARDIOLOGY-A", ASSIGNED_AT);
    }
}
