package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.port.in.QuerySurgeryAuthorityInvalidationsUseCase;
import com.mediflow.surgery.application.port.out.SurgeryAuthorityInvalidationPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

public class SurgeryAuthorityInvalidationQueryService implements QuerySurgeryAuthorityInvalidationsUseCase {
    private final SurgeryAuthorityInvalidationPort invalidations;
    private final SurgeryClockPort clock;
    public SurgeryAuthorityInvalidationQueryService(SurgeryAuthorityInvalidationPort invalidations, SurgeryClockPort clock) {
        this.invalidations = invalidations; this.clock = clock;
    }
    @Override @Transactional(readOnly = true, timeout = 5)
    public List<Candidate> findDue(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Authority batch must be between 1 and 100");
        return List.copyOf(invalidations.findDue(clock.now(), limit));
    }
}
