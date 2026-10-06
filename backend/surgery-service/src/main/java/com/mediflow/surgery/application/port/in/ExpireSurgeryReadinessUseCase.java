package com.mediflow.surgery.application.port.in;

/** Re-evaluate a due candidate under the case lock; no caller-supplied expiry or state. */
public interface ExpireSurgeryReadinessUseCase {
    boolean expire(QueryExpiredSurgeryReadinessUseCase.Candidate candidate, String correlationId);
}
