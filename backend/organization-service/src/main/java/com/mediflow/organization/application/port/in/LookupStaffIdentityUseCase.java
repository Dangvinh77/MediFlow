package com.mediflow.organization.application.port.in;

import com.mediflow.organization.application.dto.response.StaffIdentityLookupDTO;

import java.util.UUID;

/** Service-only staff identity lookup port. */
public interface LookupStaffIdentityUseCase {

    StaffIdentityLookupDTO lookup(UUID staffId);
}
