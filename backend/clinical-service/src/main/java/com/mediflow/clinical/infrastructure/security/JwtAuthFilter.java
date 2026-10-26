package com.mediflow.clinical.infrastructure.security;

import com.mediflow.common.security.JwtClaims;
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
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Verifies Bearer tokens locally before controller role checks run. */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_PREFIX = "ROLE_";

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

        if (SecurityContextHolder.getContext().getAuthentication() == null
                && !authenticate(request, response)) {
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean authenticate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization)
                || !authorization.startsWith(BEARER_PREFIX)) {
            return true;
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
            Date expiration = claims.getExpiration();
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (isScheduledPaymentInternalPath(path)) {
                Date issued = claims.getIssuedAt();
                long now = System.currentTimeMillis();
                boolean exactMethod = path.endsWith("/payment-locks")
                        ? "POST".equals(request.getMethod())
                        : path.endsWith("/payment-resolution")
                                ? "PUT".equals(request.getMethod()) : "GET".equals(request.getMethod());
                if (!exactMethod || !"billing-service".equals(subject)
                        || !JwtClaims.SERVICE_TOKEN_TYPE.equals(tokenType) || !"SYSTEM".equals(role)
                        || issued == null || expiration == null || expiration.getTime() <= now
                        || issued.getTime() > now + 5000 || expiration.getTime() <= issued.getTime()
                        || expiration.getTime() - issued.getTime() > 60000) {
                    SecurityContextHolder.clearContext();
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    return false;
                }
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        new ClinicalServicePrincipal(subject), null,
                        List.of(new SimpleGrantedAuthority("ROLE_SYSTEM"))));
                return true;
            }
            boolean prescriptionContextLookup = path.matches("/api/v1/records/[^/]+/prescription-context");
            boolean labOrderContextLookup = path.matches("/api/v1/lab-orders/[^/]+/context");
            if (prescriptionContextLookup || labOrderContextLookup) {
                Date issued = claims.getIssuedAt();
                long now = System.currentTimeMillis();
                boolean exactPath = prescriptionContextLookup
                        ? path.matches("/api/v1/records/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/prescription-context")
                        : path.matches("/api/v1/lab-orders/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/context");
                boolean exactService = prescriptionContextLookup
                        ? "pharmacy-service".equals(subject)
                        : "lab-service".equals(subject);
                UUID actorStaffId = null;
                String actorRole = null;
                if (labOrderContextLookup) {
                    try {
                        String actorStaffClaim = claims.get(JwtClaims.STAFF_ID, String.class);
                        actorStaffId = actorStaffClaim == null ? null : UUID.fromString(actorStaffClaim);
                        if (actorStaffId != null && !actorStaffId.toString().equals(actorStaffClaim)) {
                            actorStaffId = null;
                        }
                        actorRole = claims.get("originalActorRole", String.class);
                    } catch (IllegalArgumentException invalidActor) {
                        SecurityContextHolder.clearContext();
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                        return false;
                    }
                }
                boolean validActor = prescriptionContextLookup
                        || actorStaffId != null && ("DOCTOR".equals(actorRole) || "ADMIN".equals(actorRole));
                if (!exactPath || !"GET".equals(request.getMethod()) || !JwtClaims.SERVICE_TOKEN_TYPE.equals(tokenType)
                        || !"SYSTEM".equals(role) || !exactService || !validActor
                        || issued == null || expiration == null || expiration.getTime() <= now
                        || issued.getTime() > now + 5000 || expiration.getTime() <= issued.getTime()
                        || expiration.getTime() - issued.getTime() > 60000) {
                    SecurityContextHolder.clearContext();
                    if (labOrderContextLookup) {
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                        return false;
                    }
                    return true;
                }
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        new ClinicalServicePrincipal(subject, actorStaffId, actorRole), null,
                        List.of(new SimpleGrantedAuthority("ROLE_SYSTEM"))));
                return true;
            }
            if (!JwtClaims.ACCESS_TOKEN_TYPE.equals(tokenType)
                    || expiration == null || !expiration.after(new Date())
                    || !StringUtils.hasText(subject) || !StringUtils.hasText(role)) {
                SecurityContextHolder.clearContext();
                return true;
            }

            String staffIdClaim = claims.get(JwtClaims.STAFF_ID, String.class);
            UUID staffId = staffIdClaim == null ? null : UUID.fromString(staffIdClaim);
            String patientIdClaim = claims.get(JwtClaims.PATIENT_ID, String.class);
            UUID patientId = patientIdClaim == null ? null : UUID.fromString(patientIdClaim);
            if ((staffIdClaim != null && !staffId.toString().equals(staffIdClaim))
                    || (patientIdClaim != null && !patientId.toString().equals(patientIdClaim))) {
                SecurityContextHolder.clearContext();
                return true;
            }
            var authentication = new UsernamePasswordAuthenticationToken(
                    new ClinicalPrincipal(subject, role, staffId, patientId),
                    null,
                    List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role)));
            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            if (isLabOrderContextLookup(request)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return false;
            }
            return true;
        }
    }

    private static boolean isLabOrderContextLookup(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.matches("/api/v1/lab-orders/[^/]+/context");
    }

    private static boolean isScheduledPaymentInternalPath(String path) {
        return path.matches("/api/v2/clinical/reservations/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/payment-locks")
                || path.matches("/api/v2/clinical/reservations/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/payment-resolution")
                || path.matches("/api/v2/clinical/reservations/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/payment-context");
    }
}
