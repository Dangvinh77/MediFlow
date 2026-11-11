package com.mediflow.lab.application.port.out;

import java.util.UUID;

/** True when a test's results may only be released through the review workflow. */
@FunctionalInterface
public interface LabResultWorkflowGate {
    boolean reviewRequired(UUID testId);

    static LabResultWorkflowGate never() {
        return testId -> false;
    }
}
