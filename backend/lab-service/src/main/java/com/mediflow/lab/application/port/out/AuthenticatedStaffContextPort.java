package com.mediflow.lab.application.port.out;

import java.util.Optional;

/** Reads the staff identity and role from the authenticated request context. */
public interface AuthenticatedStaffContextPort {

    Optional<AuthenticatedStaffContext> currentStaff();
}
