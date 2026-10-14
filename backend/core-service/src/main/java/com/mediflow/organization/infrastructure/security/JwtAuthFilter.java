package com.mediflow.organization.infrastructure.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.mediflow.common.security.JwtClaims;
import com.mediflow.common.security.Roles;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Verifies Bearer tokens locally before method-level role checks run. */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_PREFIX = "ROLE_";
    private static final Set<String> HUMAN_ROLES = Set.of(
            Roles.ADMIN,
            Roles.DOCTOR,
            Roles.NURSE,
            Roles.PHARMACIST,
            Roles.CASHIER,
            Roles.LAB_TECH,
            Roles.MANAGER,
            Roles.PATIENT);
    private static final Set<String> HUMAN_IDENTITY_CLAIMS = Set.of(
            JwtClaims.STAFF_ID,
            JwtClaims.DEPARTMENT_ID,
            JwtClaims.PATIENT_ID);

    private final SecretKey signingKey;

    public JwtAuthFilter(JwtProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(
                properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(request);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            return;
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(authorization.substring(BEARER_PREFIX.length()))
                    .getPayload();
            String subject = claims.getSubject();
            String role = claims.get(JwtClaims.ROLE, String.class);
            String tokenType = claims.get(JwtClaims.TYPE, String.class);
            if (!StringUtils.hasText(role) || !StringUtils.hasText(tokenType)) {
                SecurityContextHolder.clearContext();
                return;
            }

            if (JwtClaims.SERVICE_TOKEN_TYPE.equals(tokenType)) {
                if (!StringUtils.hasText(subject)
                        || !Roles.SYSTEM.equals(role)
                        || containsHumanIdentityClaim(claims)) {
                    SecurityContextHolder.clearContext();
                    return;
                }
                String path = request.getRequestURI();
                boolean surgeryAuthorityLookup = path.matches("/api/v1/org/operating-rooms/[^/]+/lookup")
                        || path.matches("/api/v1/org/staff/[^/]+/surgery-eligibility");
                if (surgeryAuthorityLookup && (claims.getIssuedAt() == null || claims.getExpiration() == null
                        || claims.getExpiration().toInstant().isAfter(claims.getIssuedAt().toInstant().plusSeconds(60))
                        || claims.getIssuedAt().toInstant().isAfter(java.time.Instant.now().plusSeconds(5)))) {
                    SecurityContextHolder.clearContext();
                    return;
                }
                var authentication = new UsernamePasswordAuthenticationToken(
                        subject,
                        null,
                        List.of(
                                new SimpleGrantedAuthority(ROLE_PREFIX + role),
                                new SimpleGrantedAuthority("ROLE_SYSTEM_SERVICE")));
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                return;
            }

            if (!JwtClaims.ACCESS_TOKEN_TYPE.equals(tokenType)
                    || !isValidHumanIdentity(claims, subject, role)) {
                SecurityContextHolder.clearContext();
                return;
            }

            var authentication = new UsernamePasswordAuthenticationToken(
                    subject,
                    null,
                    List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role)));
            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
        }
    }

    private static boolean isValidHumanIdentity(Claims claims, String subject, String role) {
        if (!StringUtils.hasText(subject)
                || !isUuid(subject)
                || !HUMAN_ROLES.contains(role)
                || !hasValidOptionalUuidClaims(claims)) {
            return false;
        }

        boolean hasPatientId = claims.containsKey(JwtClaims.PATIENT_ID);
        boolean hasStaffId = claims.containsKey(JwtClaims.STAFF_ID);
        if (Roles.PATIENT.equals(role)) {
            return hasPatientId && !hasStaffId;
        }
        return !hasPatientId;
    }

    private static boolean hasValidOptionalUuidClaims(Claims claims) {
        return HUMAN_IDENTITY_CLAIMS.stream()
                .filter(claims::containsKey)
                .allMatch(claim -> isUuid(claims.get(claim, String.class)));
    }

    private static boolean containsHumanIdentityClaim(Claims claims) {
        return HUMAN_IDENTITY_CLAIMS.stream().anyMatch(claims::containsKey);
    }

    private static boolean isUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            UUID parsed = UUID.fromString(value);
            return parsed.toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
