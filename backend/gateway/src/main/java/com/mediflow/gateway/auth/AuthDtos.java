package com.mediflow.gateway.auth;

/** Auth request/response payloads. */
public final class AuthDtos {

    public record LoginRequest(String username, String password) {
    }

    public record RefreshRequest(String refreshToken) {
    }

    public record LoginResponse(String accessToken, String refreshToken, String role) {
    }

    public record RefreshResponse(String accessToken) {
    }

    private AuthDtos() {
    }
}
