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
            Date expiration = claims.getExpiration();
            if (!JwtClaims.ACCESS_TOKEN_TYPE.equals(tokenType)
                    || expiration == null || !expiration.after(new Date())
                    || !StringUtils.hasText(subject) || !StringUtils.hasText(role)) {
                SecurityContextHolder.clearContext();
                return;
            }

            String staffIdClaim = claims.get(JwtClaims.STAFF_ID, String.class);
            UUID staffId = staffIdClaim == null ? null : UUID.fromString(staffIdClaim);
            var authentication = new UsernamePasswordAuthenticationToken(
                    new ClinicalPrincipal(subject, role, staffId),
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
