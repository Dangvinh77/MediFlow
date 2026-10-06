package com.mediflow.organization.application.dto.request;

import jakarta.validation.constraints.*;
import java.util.UUID;

/** expectedRevision=0 creates; revisions >=1 update without lost writes. */
public record OperatingRoomRequest(@NotNull @PositiveOrZero Long expectedRevision,
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,40}") String roomCode,
        @NotBlank @Size(max=100) String roomName, @NotNull UUID departmentId,
        @NotNull Boolean active, @NotBlank @Size(max=500) String reason) {}
