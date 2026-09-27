package com.mediflow.lab.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** Reads the authenticated user's explicit staffId claim at the HTTP boundary. */
public interface AuthenticatedStaffIdPort {

    Optional<UUID> currentStaffId();
}
