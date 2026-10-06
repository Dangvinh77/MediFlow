package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.port.in.RecordSurgeryAuthorityInvalidationRetryUseCase;
import com.mediflow.surgery.application.port.in.QuerySurgeryAuthorityInvalidationsUseCase.Candidate;
import com.mediflow.surgery.application.port.out.SurgeryAuthorityInvalidationPort;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class SurgeryAuthorityInvalidationRetryService implements RecordSurgeryAuthorityInvalidationRetryUseCase {
    private final SurgeryCaseRepositoryPort cases;
    private final SurgeryAuthorityInvalidationPort invalidations;
    private final SurgeryClockPort clock;
    public SurgeryAuthorityInvalidationRetryService(SurgeryCaseRepositoryPort cases, SurgeryAuthorityInvalidationPort invalidations, SurgeryClockPort clock) {
        this.cases = cases; this.invalidations = invalidations; this.clock = clock;
    }
    @Override @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public void defer(Candidate candidate, String failureCode) {
        if (candidate == null || failureCode == null || !failureCode.matches("[A-Za-z0-9_]{1,64}")) {
            throw new IllegalArgumentException("Safe authority retry identity required");
        }
        if (cases.lockById(candidate.surgeryCaseId()).isEmpty()) return;
        var now = clock.now();
        if (invalidations.lockPending(candidate, now).isPresent()) invalidations.defer(candidate, failureCode, now);
    }
}
