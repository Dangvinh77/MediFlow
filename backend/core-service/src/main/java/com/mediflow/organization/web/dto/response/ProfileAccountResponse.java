package com.mediflow.organization.web.dto.response;

import com.mediflow.organization.application.dto.response.ProfileAccountLookupDTO;

public record ProfileAccountResponse(AccountResponse account, String profileEmail, boolean emailSyncPending) {
    public static ProfileAccountResponse from(ProfileAccountLookupDTO result) {
        return new ProfileAccountResponse(result.account() == null ? null : AccountResponse.from(result.account()),
                result.profileEmail(), result.emailSyncPending());
    }
}
