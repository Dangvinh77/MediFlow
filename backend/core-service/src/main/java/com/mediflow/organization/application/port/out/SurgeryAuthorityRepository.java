package com.mediflow.organization.application.port.out;

import com.mediflow.organization.domain.model.*;
import java.util.Optional;
import java.util.UUID;

public interface SurgeryAuthorityRepository {
    Optional<OperatingRoom> findRoom(UUID roomId);
    void saveRoom(OperatingRoom room, long expectedRevision);
    Optional<SurgicalCapability> findCapability(UUID staffId, SurgicalTeamRole role);
    void saveCapability(SurgicalCapability capability, long expectedRevision);
}
