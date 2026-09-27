package com.mediflow.lab.application.port.out;

import java.util.UUID;

/** Authenticated staff identity and role supplied by the verified access token. */
public record AuthenticatedStaffContext(UUID staffId, String role) {}
