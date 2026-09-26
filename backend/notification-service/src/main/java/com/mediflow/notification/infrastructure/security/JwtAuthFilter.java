package com.mediflow.notification.infrastructure.security;

import com.mediflow.common.security.JwtClaims;
import com.mediflow.common.security.Roles;
import com.mediflow.notification.application.dto.command.CallerIdentity;
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
import java.util.UUID;

/**
 * Servlet filter xác minh JWT cho mọi HTTP request vào notification-service.
 *
 * <p>{@code sub} luôn là {@code accountId} — không bao giờ được coi là patientId
 * (docs/handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md). Với vai trò {@code PATIENT},
 * danh tính bệnh nhân đến từ claim {@code patientId} ký riêng; thiếu claim này principal mang
 * {@code patientId = null} thay vì lấy tạm {@code accountId}, để tầng application (BR-N6) từ chối
 * thay vì so trùng nhầm. Chỉ token {@code type=access} (người) hoặc {@code type=service} với
 * {@code role=SYSTEM} (service-to-service, dùng cho {@code POST /send}) được xác thực; token
 * {@code refresh} hoặc sai cặp type/role bị bỏ qua, để request đi tiếp ở trạng thái chưa xác thực.
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
            String tokenType = claims.get(JwtClaims.TYPE, String.class);
            if (!StringUtils.hasText(subject) || !StringUtils.hasText(role)) {
                SecurityContextHolder.clearContext();
                return;
            }

            if (JwtClaims.SERVICE_TOKEN_TYPE.equals(tokenType)) {
                authenticateService(request, subject, role);
                return;
            }

            if (!JwtClaims.ACCESS_TOKEN_TYPE.equals(tokenType) || Roles.SYSTEM.equals(role)) {
                SecurityContextHolder.clearContext();
                return;
            }

            UUID accountId = UUID.fromString(subject);
            UUID patientId = Roles.PATIENT.equals(role)
                    ? optionalUuid(claims.get(JwtClaims.PATIENT_ID))
                    : null;
            CallerIdentity identity = new CallerIdentity(accountId, patientId, role);

            var authentication = new UsernamePasswordAuthenticationToken(
                    identity, null, List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role)));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
        }
    }

    /** Service-to-service caller: subject is a service name, never an accountId UUID. */
    private void authenticateService(HttpServletRequest request, String subject, String role) {
        if (!Roles.SYSTEM.equals(role)) {
            SecurityContextHolder.clearContext();
            return;
        }
        var authentication = new UsernamePasswordAuthenticationToken(
                subject, null, List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role)));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /** Parses an optional signed identity claim; malformed/absent claims fail closed to null. */
    private UUID optionalUuid(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
