package com.mediflow.report.web;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

final class ReportScopeTestSupport {
    static final UUID ACCOUNT=UUID.fromString("00000000-0000-4000-8000-000000000001");
    static String bearer(String role,String secret) {
        return "Bearer "+io.jsonwebtoken.Jwts.builder().subject(ACCOUNT.toString()).claim("type","access").claim("role",role)
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8))).compact();
    }
}
