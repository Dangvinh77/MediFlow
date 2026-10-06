package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.port.in.RecordSurgeryReadinessExpiryRetryUseCase;
import com.mediflow.surgery.application.port.in.QueryExpiredSurgeryReadinessUseCase.Candidate;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryReadinessExpiryPort;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class SurgeryReadinessExpiryRetryService implements RecordSurgeryReadinessExpiryRetryUseCase {
    private final SurgeryReadinessExpiryPort expiry;
    private final SurgeryClockPort clock;
    public SurgeryReadinessExpiryRetryService(SurgeryReadinessExpiryPort expiry, SurgeryClockPort clock) {
        this.expiry = expiry; this.clock = clock;
    }
    @Override @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public void defer(Candidate candidate, String failureCode) {
        if (candidate == null || failureCode == null || !failureCode.matches("[A-Za-z0-9_]{1,64}")) {
            throw new IllegalArgumentException("Safe readiness expiry failure code is required");
        }
        expiry.deferIfStillDue(candidate, clock.now(), failureCode);
    }
}
