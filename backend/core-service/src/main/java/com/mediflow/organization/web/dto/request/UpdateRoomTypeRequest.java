package com.mediflow.organization.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Renames a room type; its category and room-count rule are fixed. */
public record UpdateRoomTypeRequest(
        @NotBlank @Size(min = 2, max = 100) String name,
        @Size(max = 255) String description) {}
