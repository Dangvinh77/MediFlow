package com.mediflow.lab.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mediflow.lab.domain.model.LabSpecimen;

public interface LabSpecimenRepositoryPort {
    Optional<LabSpecimen> findById(UUID specimenId);

    Optional<LabSpecimen> findByIdForUpdate(UUID specimenId);

    /** Every specimen linked to a test, oldest first. */
    List<LabSpecimen> forTest(UUID testId);

    boolean hasAcceptedSpecimen(UUID testId);

    void insert(LabSpecimen specimen);

    /** Stores the reception decision. */
    void update(LabSpecimen specimen);
}
