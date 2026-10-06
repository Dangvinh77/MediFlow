package com.mediflow.surgery.application.port.in;

public interface RecordSurgeryAuthorityInvalidationRetryUseCase {
    void defer(QuerySurgeryAuthorityInvalidationsUseCase.Candidate candidate, String failureCode);
}
