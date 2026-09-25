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
            "/actuator/health",
            "/actuator/info"
    );
    private static final Set<String> HUMAN_ROLES = Set.of(
            Roles.ADMIN, Roles.DOCTOR, Roles.NURSE, Roles.PHARMACIST,
            Roles.CASHIER, Roles.LAB_TECH, Roles.MANAGER, Roles.PATIENT);
    private static final UUID INVALID_UUID = new UUID(0L, 0L);
    private final JwtTokenService jwt;
    private final GatewayErrorResponseWriter errors;

    public JwtAuthenticationFilter(
            JwtTokenService jwt,
            GatewayErrorResponseWriter errors) {
        this.jwt = jwt;
        this.errors = errors;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (isPublic(path)) {
            return chain.filter(exchange);
        }

        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing bearer token");
        }

        try {
            Claims claims = jwt.parse(auth.substring(7));
            String tokenType = claims.get(JwtClaims.TYPE, String.class);
            String role = claims.get(JwtClaims.ROLE, String.class);
            if (JwtClaims.SERVICE_TOKEN_TYPE.equals(tokenType)) {
                if (!isValidServiceToken(claims)) {
                    return unauthorized(exchange, "Invalid service token");
                }
                if (!isInternalOnly(path) && !Roles.SYSTEM.equals(role)) {
                    return unauthorized(exchange, "Invalid service token role");
                }
                return continueWithIdentity(exchange, chain, claims, Roles.SYSTEM, true);
            }

            if (!JwtClaims.ACCESS_TOKEN_TYPE.equals(tokenType)
                    || isInternalOnly(path)
                    || claims.getSubject() == null
                    || claims.getSubject().isBlank()
                    || role == null
                    || !HUMAN_ROLES.contains(role)
                    || !isUuid(claims.getSubject())
                    || !hasValidIdentityClaims(claims, role)) {
                if (isInternalOnly(path)
                        && JwtClaims.ACCESS_TOKEN_TYPE.equals(tokenType)
                        && HUMAN_ROLES.contains(role)) {
                    return forbidden(exchange, "Service token is required");
                }
                return unauthorized(exchange, "Invalid access token");
            }
            return continueWithIdentity(exchange, chain, claims, role, false);
        } catch (Exception e) {
            log.debug("Rejected request to {} — invalid token: {}", path, e.getMessage());
            return unauthorized(exchange, "Invalid or expired token");
        }
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private boolean isInternalOnly(String path) {
        return "/api/v1/org/accounts/verify".equals(path)
                || path.matches("/api/v1/org/staff/[^/]+/(exists|lookup)")
                || path.matches("/api/v1/org/departments/[^/]+/lookup")
                || path.matches("/api/v1/patients/[^/]+/exists");
    }

    private boolean isValidServiceToken(Claims claims) {
        return Roles.SYSTEM.equals(claims.get(JwtClaims.ROLE, String.class))
                && claims.getSubject() != null
                && !claims.getSubject().isBlank()
                && optionalUuid(claims, JwtClaims.PATIENT_ID) == null
                && optionalUuid(claims, JwtClaims.STAFF_ID) == null
                && optionalUuid(claims, JwtClaims.DEPARTMENT_ID) == null;
    }

    private boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private Mono<Void> continueWithIdentity(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            Claims claims,
            String role,
            boolean serviceToken) {
        String correlationId = exchange.getRequest().getHeaders()
                .getFirst(JwtClaims.HEADER_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = CorrelationIdWebFilter.normalize(null);
        }
        String downstreamCorrelationId = correlationId;
        var requestBuilder = exchange.getRequest().mutate();
        requestBuilder.headers(headers -> {
            headers.remove("X-User-Id");
            headers.remove("X-User-Role");
            headers.remove("X-Staff-Id");
            headers.remove("X-Department-Id");
            headers.remove("X-Patient-Id");
            headers.remove("X-Service-Id");
            headers.set(JwtClaims.HEADER_CORRELATION_ID, downstreamCorrelationId);
            headers.set("X-User-Role", role);
            if (serviceToken) {
                headers.set("X-Service-Id", claims.getSubject());
            } else {
                headers.set("X-User-Id", claims.getSubject());
                setClaimHeader(headers, claims, "X-Staff-Id", JwtClaims.STAFF_ID);
                setClaimHeader(headers, claims, "X-Department-Id", JwtClaims.DEPARTMENT_ID);
                setClaimHeader(headers, claims, "X-Patient-Id", JwtClaims.PATIENT_ID);
            }
        });
        ServerHttpRequest mutated = requestBuilder.build();
        exchange.getAttributes().put(RouteAuthorizationFilter.ROLE_ATTRIBUTE, role);
        return chain.filter(exchange.mutate().request(mutated).build());
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
        return errors.write(exchange, HttpStatus.UNAUTHORIZED,
                "AUTH_UNAUTHORIZED", reason);
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String reason) {
        return errors.write(exchange, HttpStatus.FORBIDDEN,
                "AUTH_FORBIDDEN", reason);
    }

    @Override
    public int getOrder() {
        return -1; // run before routing
    }
}
