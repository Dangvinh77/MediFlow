package com.mediflow.organization.application.port.in;

import com.mediflow.organization.domain.model.Staff;
import java.util.UUID;

public interface DeactivateStaffUseCase {
    Staff execute(UUID staffId, UUID actorAccountId);
}
