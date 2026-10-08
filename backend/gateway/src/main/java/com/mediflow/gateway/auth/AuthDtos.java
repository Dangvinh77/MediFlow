package com.mediflow.gateway.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Auth request/response payloads. */
public final class AuthDtos {

    public record LoginRequest(
            @NotBlank @Size(max = 50) String username,
            @NotBlank @Size(max = 72) String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LoginResponse(String accessToken, String refreshToken, String role) {
    }

    public record RefreshResponse(String accessToken) {
    }

    private AuthDtos() {
    }
}
