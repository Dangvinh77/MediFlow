package com.mediflow.organization.web.dto.request;

import jakarta.validation.constraints.NotNull;

public record RoomStatusRequest(@NotNull(message = "Status is required") Boolean active) {}
