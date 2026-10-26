package com.mediflow.organization.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Only contact fields can be bound; employment, identity and role fields are never accepted here. */
public record UpdateOwnStaffContactRequest(
        @Pattern(regexp = "\\d{10,15}") String phoneNumber,
        @Email @Size(max = 100) String email,
        @Size(max = 255) String address) { }
