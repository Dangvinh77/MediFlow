package com.mediflow.clinical.application.port.out;

import java.util.UUID;
import java.util.Optional;
import com.mediflow.clinical.application.exception.UpstreamUnavailableException;

/** BR-A4: adapter must validate doctor eligibility as well as department membership. */
public interface StaffLookupPort {
    /**
     * @return the doctor's department, or empty for a confirmed missing/ineligible doctor
     * @throws UpstreamUnavailableException on timeout, transport errors or unusable upstream responses
     */
    Optional<UUID> departmentOf(UUID staffId);
}
