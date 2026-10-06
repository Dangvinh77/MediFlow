package com.mediflow.organization.application.port.in;

import com.mediflow.organization.application.dto.request.*;
import com.mediflow.organization.application.dto.response.*;
import com.mediflow.organization.domain.model.SurgicalTeamRole;
import java.time.Instant;
import java.util.UUID;

public interface ManageSurgeryAuthorityUseCase {
    OperatingRoomDTO saveRoom(UUID roomId, OperatingRoomRequest request, UUID actorAccountId);
    SurgicalCapabilityDTO decideCapability(UUID staffId, SurgicalTeamRole role,
            SurgicalCapabilityRequest request, UUID actorAccountId);
    OperatingRoomLookupDTO lookupRoom(UUID roomId);
    SurgicalEligibilityDTO lookupEligibility(UUID staffId, SurgicalTeamRole role,
            Instant startsAt, Instant endsAt);
}
