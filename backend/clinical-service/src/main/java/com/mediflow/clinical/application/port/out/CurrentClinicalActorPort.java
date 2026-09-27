package com.mediflow.clinical.application.port.out;

import com.mediflow.clinical.application.dto.command.ActorIdentity;

public interface CurrentClinicalActorPort {
    ActorIdentity current();
}
