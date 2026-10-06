package com.mediflow.surgery.infrastructure.config;

import com.mediflow.surgery.application.port.in.ExpireSurgeryReadinessUseCase;
import com.mediflow.surgery.application.port.in.QueryExpiredSurgeryReadinessUseCase;
import com.mediflow.surgery.application.port.in.RecordSurgeryReadinessExpiryRetryUseCase;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Each candidate is handled through a separately committed application use case. */
public class SurgeryReadinessExpiryWorker {
    private static final Logger LOG = LoggerFactory.getLogger(SurgeryReadinessExpiryWorker.class);
    private final QueryExpiredSurgeryReadinessUseCase query;
    private final ExpireSurgeryReadinessUseCase expire;
    private final RecordSurgeryReadinessExpiryRetryUseCase retry;
    private final int batchSize;

    public SurgeryReadinessExpiryWorker(QueryExpiredSurgeryReadinessUseCase query,
            ExpireSurgeryReadinessUseCase expire, RecordSurgeryReadinessExpiryRetryUseCase retry, int batchSize) {
        if (batchSize < 1 || batchSize > 100) throw new IllegalArgumentException("Expiry batch size must be 1..100");
        this.query = query; this.expire = expire; this.retry = retry; this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${mediflow.surgery.readiness.expiry.poll-interval-ms:5000}",
            initialDelayString = "${mediflow.surgery.readiness.expiry.poll-interval-ms:5000}")
    public void runBatch() {
        List<QueryExpiredSurgeryReadinessUseCase.Candidate> candidates;
        try {
            candidates = query.findDue(batchSize);
        } catch (RuntimeException failure) {
            LOG.warn("Surgery readiness expiry query failed: {}", failureCode(failure));
            return;
        }
        for (var candidate : candidates) {
            String correlation = UUID.randomUUID().toString();
            try {
                expire.expire(candidate, correlation);
            } catch (RuntimeException failure) {
                LOG.warn("Surgery readiness expiry failed; correlation={}, case={}, code={}",
                        correlation, candidate.surgeryCaseId(), failureCode(failure));
                try {
                    retry.defer(candidate, failureCode(failure));
                } catch (RuntimeException retryFailure) {
                    // The original snapshot remains due; no state is discarded on retry-storage failure.
                    LOG.warn("Surgery readiness expiry retry could not be persisted; correlation={}, code={}",
                            correlation, failureCode(retryFailure));
                }
            }
        }
    }

    private static String failureCode(RuntimeException failure) {
        String name = failure.getClass().getSimpleName().replaceAll("[^A-Za-z0-9_]", "_");
        return name.isEmpty() ? "RuntimeException" : name.substring(0, Math.min(64, name.length()));
    }
}
