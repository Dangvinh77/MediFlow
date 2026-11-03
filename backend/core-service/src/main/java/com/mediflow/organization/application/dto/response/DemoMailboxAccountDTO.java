package com.mediflow.organization.application.dto.response;

import java.util.UUID;

public record DemoMailboxAccountDTO(UUID accountId, String role, String displayName, String email) { }
