package com.mediflow.inpatient.infrastructure.security;

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

/** Verifies Bearer tokens locally before future controller role checks run. */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_PREFIX = "ROLE_";
    private static final Set<String> HUMAN_ROLES = Set.of(
            Roles.ADMIN, Roles.DOCTOR, Roles.NURSE, Roles.PHARMACIST,
            Roles.CASHIER, Roles.LAB_TECH, Roles.MANAGER, Roles.PATIENT);

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
        if (!StringUtils.hasText(authorization)
                || !authorization.startsWith(BEARER_PREFIX)) {
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
            if (!JwtClaims.ACCESS_TOKEN_TYPE.equals(tokenType)
                    || !StringUtils.hasText(subject)
                    || !StringUtils.hasText(role)
                    || !HUMAN_ROLES.contains(role)
                    || claims.getExpiration() == null) {
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
}
