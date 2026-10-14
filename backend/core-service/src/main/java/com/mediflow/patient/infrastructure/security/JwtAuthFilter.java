package com.mediflow.patient.infrastructure.security;

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
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class JwtAuthFilter extends OncePerRequestFilter {
    private static final String PREFIX = "Bearer ";
    private final SecretKey signingKey;
    private static final Set<String> HUMAN_ROLES = Set.of(Roles.ADMIN, Roles.DOCTOR, Roles.NURSE,
            Roles.PHARMACIST, Roles.CASHIER, Roles.LAB_TECH, Roles.MANAGER, Roles.PATIENT);

    public JwtAuthFilter(JwtProperties properties) {
        signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) authenticate(request);
        chain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith(PREFIX)) return;
        try {
            Claims claims = Jwts.parser().verifyWith(signingKey).build()
                    .parseSignedClaims(header.substring(PREFIX.length())).getPayload();
            String type = claims.get(JwtClaims.TYPE, String.class);
            String role = claims.get(JwtClaims.ROLE, String.class);
            String subject = claims.getSubject();
            if (!StringUtils.hasText(type) || !StringUtils.hasText(role) || !StringUtils.hasText(subject)) return;
            if (JwtClaims.ACCESS_TOKEN_TYPE.equals(type)) {
                validateHuman(claims, role, subject);
            } else if (JwtClaims.SERVICE_TOKEN_TYPE.equals(type)) {
                if (!Roles.SYSTEM.equals(role) || hasIdentityClaim(claims)) return;
                setAuthentication(request, subject, Roles.SYSTEM);
            }
        } catch (JwtException | IllegalArgumentException ignored) {
            SecurityContextHolder.clearContext();
        }
    }

    private void validateHuman(Claims claims, String role, String subject) {
        if (!HUMAN_ROLES.contains(role) || !isUuid(subject)) return;
        if (Roles.PATIENT.equals(role)) {
            if (!isUuid(claims.get(JwtClaims.PATIENT_ID, String.class))
                    || claims.get(JwtClaims.STAFF_ID) != null) return;
        } else if (claims.get(JwtClaims.PATIENT_ID) != null) return;
        setAuthentication(null, subject, role);
    }

    private boolean hasIdentityClaim(Claims claims) {
        return claims.get(JwtClaims.PATIENT_ID) != null || claims.get(JwtClaims.STAFF_ID) != null
                || claims.get(JwtClaims.DEPARTMENT_ID) != null;
    }

    private boolean isUuid(String value) {
        if (!StringUtils.hasText(value)) return false;
        try { UUID.fromString(value); return true; } catch (IllegalArgumentException ignored) { return false; }
    }

    private void setAuthentication(HttpServletRequest request, String subject, String role) {
        var auth = new UsernamePasswordAuthenticationToken(subject, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        if (request != null) auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
