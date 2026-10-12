package com.mediflow.inpatient.application.service;

import com.mediflow.inpatient.application.dto.response.PlacementLookupDTO;
import com.mediflow.inpatient.application.port.in.LookupPlacementAuthorityUseCase;
import com.mediflow.inpatient.application.port.out.PlacementAuthorityRepositoryPort;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

public class LookupPlacementAuthorityService implements LookupPlacementAuthorityUseCase {
    private final PlacementAuthorityRepositoryPort placements;
    private final Clock clock;

    public LookupPlacementAuthorityService(PlacementAuthorityRepositoryPort placements, Clock clock) {
        this.placements = Objects.requireNonNull(placements);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public PlacementLookupDTO lookup(UUID admissionId) {
        Objects.requireNonNull(admissionId, "admissionId");
        var snapshot = placements.findByAdmissionId(admissionId).orElse(null);
        if (snapshot == null) {
            return new PlacementLookupDTO(false, admissionId, null, null,
                    false, 0, null, clock.instant());
        }
        PlacementLookupDTO.PlacementDTO placement = snapshot.hasPlacement()
                ? new PlacementLookupDTO.PlacementDTO(snapshot.assignmentId(), snapshot.bedId(),
                        snapshot.placementDepartmentId(), snapshot.wardCode(), snapshot.assignedAt())
                : null;
        return new PlacementLookupDTO(true, snapshot.admissionId(), snapshot.patientId(),
                snapshot.admissionStatus().name(), snapshot.medicationWindowOpen(),
                snapshot.placementRevision(), placement, clock.instant());
    }
}
