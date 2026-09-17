package com.mediflow.organization.web.dto.response;

import java.util.UUID;

import com.mediflow.organization.application.port.in.VerifyCredentialsUseCase.VerifiedAccount;
import com.mediflow.organization.domain.model.Role;

public record VerifiedAccountResponse(
        UUID accountId,
        UUID staffId,
        UUID departmentId,
        Role role) {

    public static VerifiedAccountResponse from(VerifiedAccount account) {
        return new VerifiedAccountResponse(
                account.accountId(),
                account.staffId(),
                account.departmentId(),
                account.role());
    }
}
