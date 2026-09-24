package com.mediflow.gateway.filter;

import com.mediflow.common.security.JwtClaims;
import com.mediflow.common.security.Roles;
import com.mediflow.gateway.security.JwtTokenService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Validates the JWT once at the edge and propagates identity downstream as headers.
 * Public paths (login/refresh, health) are skipped. See docs/ai/07-security-rbac.md.
 * Downstream services still re-verify (defense in depth).
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/actuator/health"
    );
    private static final Set<String> SUPPORTED_ROLES = Set.of(
            Roles.ADMIN, Roles.DOCTOR, Roles.NURSE, Roles.PHARMACIST,
            Roles.CASHIER, Roles.LAB_TECH, Roles.MANAGER, Roles.PATIENT);
    private static final UUID INVALID_UUID = new UUID(0L, 0L);
    private static final List<String> INTERNAL_ONLY_PATHS = List.of(
            "/api/v1/org/accounts/verify",
            "/api/v1/org/staff/");

    private final JwtTokenService jwt;

    public JwtAuthenticationFilter(JwtTokenService jwt) {
        this.jwt = jwt;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (isPublic(path)) {
            return chain.filter(exchange);
        }
        if (isInternalOnly(path)) {
            return forbidden(exchange);
        }

        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing bearer token");
        }

        try {
            Claims claims = jwt.parse(auth.substring(7));
            String tokenType = claims.get(JwtClaims.TYPE, String.class);
            String role = claims.get(JwtClaims.ROLE, String.class);
            if (!JwtClaims.ACCESS_TOKEN_TYPE.equals(tokenType)
                    || claims.getSubject() == null
                    || claims.getSubject().isBlank()
                    || role == null
                    || !SUPPORTED_ROLES.contains(role)
                    || !hasValidIdentityClaims(claims, role)) {
                return unauthorized(exchange, "Invalid access token");
            }
            String correlationId = claims.get(JwtClaims.CORRELATION_ID, String.class);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }
            String downstreamCorrelationId = correlationId;
            var requestBuilder = exchange.getRequest().mutate();
            requestBuilder.headers(headers -> {
                headers.remove("X-User-Id");
                headers.remove("X-User-Role");
                headers.remove("X-Staff-Id");
                headers.remove("X-Department-Id");
                headers.remove("X-Patient-Id");
                headers.remove(JwtClaims.HEADER_CORRELATION_ID);
                headers.set("X-User-Id", claims.getSubject());
                headers.set("X-User-Role", role);
                headers.set(JwtClaims.HEADER_CORRELATION_ID, downstreamCorrelationId);
                setClaimHeader(headers, claims, "X-Staff-Id", JwtClaims.STAFF_ID);
                setClaimHeader(headers, claims, "X-Department-Id", JwtClaims.DEPARTMENT_ID);
                setClaimHeader(headers, claims, "X-Patient-Id", JwtClaims.PATIENT_ID);
            });
            ServerHttpRequest mutated = requestBuilder.build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (Exception e) {
            log.debug("Rejected request to {} — invalid token: {}", path, e.getMessage());
            return unauthorized(exchange, "Invalid or expired token");
        }
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private boolean isInternalOnly(String path) {
        return path.equals(INTERNAL_ONLY_PATHS.get(0))
                || (path.startsWith(INTERNAL_ONLY_PATHS.get(1)) && path.endsWith("/exists"));
    }

    private void setClaimHeader(
            org.springframework.http.HttpHeaders headers,
            Claims claims,
            String headerName,
            String claimName) {
        String value = claims.get(claimName, String.class);
        if (value != null && !value.isBlank()) {
            headers.set(headerName, value);
        }
    }

    private boolean hasValidIdentityClaims(Claims claims, String role) {
        UUID patientId = optionalUuid(claims, JwtClaims.PATIENT_ID);
        UUID staffId = optionalUuid(claims, JwtClaims.STAFF_ID);
        UUID departmentId = optionalUuid(claims, JwtClaims.DEPARTMENT_ID);
        if (patientId == INVALID_UUID || staffId == INVALID_UUID || departmentId == INVALID_UUID) {
            return false;
        }
        if (Roles.PATIENT.equals(role)) {
            return patientId != null && staffId == null;
        }
        return patientId == null;
    }

    private UUID optionalUuid(Claims claims, String claimName) {
        String value = claims.get(claimName, String.class);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return INVALID_UUID;
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("X-Auth-Error", reason);
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> forbidden(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -1; // run before routing
    }
}
