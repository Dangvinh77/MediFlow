package com.mediflow.surgery.application.port.in;

public interface ApplySurgeryAuthorityInvalidationUseCase {
    boolean apply(QuerySurgeryAuthorityInvalidationsUseCase.Candidate candidate);
}
