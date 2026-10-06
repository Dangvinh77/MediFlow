package com.mediflow.surgery.infrastructure.config;

import com.mediflow.surgery.application.port.in.ApplySurgeryAuthorityInvalidationUseCase;
import com.mediflow.surgery.application.port.in.QuerySurgeryAuthorityInvalidationsUseCase;
import com.mediflow.surgery.application.port.in.RecordSurgeryAuthorityInvalidationRetryUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Separate transactions and durable backoff prevent one broken case starving healthy cases. */
public class SurgeryAuthorityInvalidationWorker {
    private static final Logger LOG = LoggerFactory.getLogger(SurgeryAuthorityInvalidationWorker.class);
    private final QuerySurgeryAuthorityInvalidationsUseCase query;
    private final ApplySurgeryAuthorityInvalidationUseCase apply;
    private final RecordSurgeryAuthorityInvalidationRetryUseCase retry;
    private final int batchSize;
    public SurgeryAuthorityInvalidationWorker(QuerySurgeryAuthorityInvalidationsUseCase query,
            ApplySurgeryAuthorityInvalidationUseCase apply, RecordSurgeryAuthorityInvalidationRetryUseCase retry, int batchSize) {
        if (batchSize < 1 || batchSize > 100) throw new IllegalArgumentException("Authority batch must be between 1 and 100");
        this.query = query; this.apply = apply; this.retry = retry; this.batchSize = batchSize;
    }
    @Scheduled(fixedDelayString = "${mediflow.surgery.messaging.organization-authority.poll-interval-ms:5000}",
            initialDelayString = "${mediflow.surgery.messaging.organization-authority.initial-delay-ms:5000}")
    public void poll() {
        final java.util.List<QuerySurgeryAuthorityInvalidationsUseCase.Candidate> candidates;
        try { candidates = query.findDue(batchSize); }
        catch (RuntimeException unavailable) { LOG.warn("Authority work query failed code={}", code(unavailable)); return; }
        for (var candidate : candidates) {
            try { apply.apply(candidate); }
            catch (RuntimeException failure) {
                LOG.warn("Authority invalidation failed case={} event={} code={}", candidate.surgeryCaseId(), candidate.eventId(), code(failure));
                try { retry.defer(candidate, code(failure)); }
                catch (RuntimeException unavailable) { LOG.warn("Authority retry persistence failed case={} code={}", candidate.surgeryCaseId(), code(unavailable)); }
            }
        }
    }
    private static String code(RuntimeException exception) {
        String code = exception.getClass().getSimpleName();
        return code.matches("[A-Za-z0-9_]{1,64}") ? code : "AuthorityInvalidationFailed";
    }
}
