package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.port.in.QueryExpiredSurgeryReadinessUseCase;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryReadinessExpiryPort;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public class ExpiredSurgeryReadinessQueryService implements QueryExpiredSurgeryReadinessUseCase {
    private final SurgeryReadinessExpiryPort expiry;
    private final SurgeryClockPort clock;
    public ExpiredSurgeryReadinessQueryService(SurgeryReadinessExpiryPort expiry, SurgeryClockPort clock) {
        this.expiry = expiry; this.clock = clock;
    }
    @Override @Transactional(readOnly = true, timeout = 5)
    public List<Candidate> findDue(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Expiry batch must be between 1 and 100");
        return List.copyOf(expiry.findDue(clock.now(), limit));
    }
}
