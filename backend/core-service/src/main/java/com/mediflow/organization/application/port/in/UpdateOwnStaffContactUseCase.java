package com.mediflow.organization.application.port.in;

import com.mediflow.organization.domain.model.Staff;
import java.util.UUID;

public interface UpdateOwnStaffContactUseCase {
    Staff update(UUID requestedStaffId, UUID signedStaffId, String phoneNumber, String email, String address);
}
