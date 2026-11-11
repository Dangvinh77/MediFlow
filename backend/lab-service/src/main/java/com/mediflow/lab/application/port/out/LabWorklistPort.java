package com.mediflow.lab.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LabWorklistPort {
    /** Tests still to be worked on, plus completed tests with an amendment in progress; oldest first. */
    List<WorklistRow> open();

    /**
     * {@code specimenStatus}: NOT_COLLECTED, COLLECTED, ACCEPTED or REJECTED (latest specimen);
     * {@code reportStatus}: newest report revision's status, null before any draft; {@code dueAt}:
     * creation time plus the catalogue turnaround, null for tests without a catalogue service.
     */
    record WorklistRow(UUID testId, UUID patientId, String labType, String testStatus, String serviceCode,
            String serviceName, String displayGroup, String specimenStatus, String reportStatus,
            Integer reportVersion, Instant dueAt) {}
}
