package com.mediflow.surgery.application.port.in;

/** Failure bookkeeping is a separate transaction and never invalidates clinical evidence. */
public interface RecordSurgeryReadinessExpiryRetryUseCase {
    void defer(QueryExpiredSurgeryReadinessUseCase.Candidate candidate, String failureCode);
}
