package com.mediflow.clinical.application.port.out;

import java.util.UUID;
import com.mediflow.clinical.application.exception.UpstreamUnavailableException;

/** Future adapter unwraps patient-service's ApiResponse; an outage is never a lookup miss. */
public interface PatientLookupPort {
    /**
     * @return false only for a confirmed missing patient
     * @throws UpstreamUnavailableException on timeout, transport errors or unusable upstream responses
     */
    boolean exists(UUID patientId);
}
