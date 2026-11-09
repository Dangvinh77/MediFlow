package com.mediflow.lab.application.port.out;

import java.util.UUID;

/** The authenticated human reader, taken from the verified access token. */
public record ReaderIdentity(UUID accountId, UUID staffId, String role, UUID departmentId) {}
