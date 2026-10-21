package com.mediflow.billing.infrastructure.security;

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
import java.util.List;

/**
 * Servlet filter xác minh JWT cho mọi HTTP request vào billing-service.
 *
 * <p>Xác thực xong, principal là {@code sub} (accountId dạng chuỗi) và authority là
 * {@code ROLE_<role>} từ claim {@code role} — billing không cần một kiểu principal riêng như
 * pharmacy's {@code ActorIdentity} vì không có khái niệm sở hữu theo staff, chỉ role-based
 * {@code @PreAuthorize} (docs/ai/07-security-rbac.md).
 *
 * <p>Token thiếu/sai để request ở trạng thái chưa xác thực; {@link com.mediflow.billing.infrastructure.config.SecurityConfig}
 * là nơi sinh 401 theo envelope chung, không phải filter này.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_PREFIX = "ROLE_";

    private final SecretKey signingKey;

    public JwtAuthFilter(JwtProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
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
            boolean lookup = "GET".equals(request.getMethod()) && request.getRequestURI().matches(
                    "/api/v1/billing/financial-clearances/[0-9a-fA-F-]{36}/lookup");
            boolean executionStart = "POST".equals(request.getMethod()) && request.getRequestURI().matches(
                    "/api/v1/billing/financial-clearances/[0-9a-fA-F-]{36}/execution-starts");
            boolean clearanceServiceCall = lookup || executionStart;
            boolean serviceToken = JwtClaims.SERVICE_TOKEN_TYPE.equals(claims.get(JwtClaims.TYPE, String.class));
            if (clearanceServiceCall || serviceToken || "SYSTEM".equals(role)) {
                var issued = claims.getIssuedAt();
                var expires = claims.getExpiration();
                var now = java.time.Instant.now();
                boolean permittedCaller = executionStart
                        ? "clinical-service".equals(subject) || "lab-service".equals(subject)
                        : "surgery-service".equals(subject) || "pharmacy-service".equals(subject)
                            || "clinical-service".equals(subject) || "lab-service".equals(subject);
                if (!clearanceServiceCall || !serviceToken || !"SYSTEM".equals(role) || !permittedCaller
                        || issued == null || expires == null || !expires.after(issued)
                        || expires.toInstant().isAfter(issued.toInstant().plusSeconds(60))
                        || issued.toInstant().isAfter(now.plusSeconds(5))) {
                    SecurityContextHolder.clearContext();
                    return;
                }
            } else {
                // Every human endpoint uses the same credential boundary, including legacy
                // invoices and newly added reads. A route list can silently omit the next API.
                if (!StringUtils.hasText(subject)
                        || !JwtClaims.ACCESS_TOKEN_TYPE.equals(claims.get(JwtClaims.TYPE, String.class))
                        || claims.getExpiration() == null) {
                    SecurityContextHolder.clearContext();
                    return;
                }
                if (!java.util.UUID.fromString(subject).toString().equalsIgnoreCase(subject)) {
                    SecurityContextHolder.clearContext();
                    return;
                }
            }
            if (!StringUtils.hasText(subject) || !StringUtils.hasText(role)) {
                SecurityContextHolder.clearContext();
                return;
            }

            var authentication = new UsernamePasswordAuthenticationToken(
                    subject, null, List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role)));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
        }
    }
}
