package com.mediflow.organization.application.dto.response;

import com.mediflow.organization.domain.model.Account;

/** Account metadata linked to one authoritative profile owner. */
public record ProfileAccountLookupDTO(Account account, String profileEmail, boolean emailSyncPending) { }
