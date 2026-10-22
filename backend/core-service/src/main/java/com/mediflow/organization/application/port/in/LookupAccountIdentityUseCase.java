package com.mediflow.organization.application.port.in;

import com.mediflow.organization.application.dto.response.AccountIdentityLookupDTO;

import java.util.UUID;

/** Service-only lookup of the current account authority used when refreshing a token. */
public interface LookupAccountIdentityUseCase {
    AccountIdentityLookupDTO lookup(UUID accountId);
}
