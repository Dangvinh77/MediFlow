package com.mediflow.pharmacy.infrastructure.security;

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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Servlet filter xác minh JWT cho mọi HTTP request đi vào pharmacy-service.
 *
 * <p>Filter đọc access token từ header {@code Authorization: Bearer <token>}, kiểm tra chữ ký HS256
 * và thời hạn token bằng JJWT, sau đó chuyển claim {@code role} thành authority theo quy ước của
 * Spring Security ({@code ROLE_ADMIN}, {@code ROLE_DOCTOR}, {@code ROLE_PHARMACIST}, ...).</p>
 *
 * <p>Filter không tin các header định danh do client tự gửi như {@code X-User-Role}. Danh tính và
 * quyền chỉ được lấy từ token có chữ ký hợp lệ. Nhờ đó việc gọi trực tiếp cổng 8085 vẫn phải qua
 * cùng cơ chế xác thực như khi request đi qua gateway.</p>
 *
 * <p>Khi token bị thiếu hoặc không hợp lệ, filter không tự ghi response. Nó để request tiếp tục ở
 * trạng thái chưa xác thực để {@link com.mediflow.pharmacy.infrastructure.config.SecurityConfig}
 * sinh response {@code 401 Unauthorized} theo envelope chung của MediFlow.</p>
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_PREFIX = "ROLE_";

    private final SecretKey signingKey;

    /**
     * Khởi tạo filter và chuyển secret cấu hình thành khóa ký dùng bởi JJWT.
     *
     * @param properties cấu hình JWT đã được kiểm tra khi bind từ application.yml hoặc biến môi trường
     */
    public JwtAuthFilter(JwtProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(
                properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Xác thực request đúng một lần trong mỗi vòng đời dispatch của servlet.
     *
     * <p>Nếu SecurityContext đã chứa authentication (ví dụ trong web slice test sử dụng
     * {@code @WithMockUser}), filter giữ nguyên context hiện tại. Trong request thực tế, filter sẽ
     * thử dựng authentication từ Bearer token trước khi chuyển request sang filter tiếp theo.</p>
     *
     * @param request request HTTP hiện tại
     * @param response response HTTP hiện tại
     * @param filterChain chuỗi filter còn lại
     * @throws ServletException nếu filter phía sau phát sinh lỗi servlet
     * @throws IOException nếu quá trình xử lý request/response phát sinh lỗi I/O
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticateFromBearerToken(request);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Đọc Bearer token, xác minh claims và ghi authentication vào SecurityContext.
     *
     * <p>Mọi {@link JwtException} (sai chữ ký, hết hạn, token hỏng) hoặc dữ liệu claim không hợp lệ
     * đều làm request trở về trạng thái chưa xác thực. Token không được ghi vào log để tránh làm lộ
     * thông tin xác thực.</p>
     *
     * @param request request chứa header Authorization
     */
    private void authenticateFromBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            return;
        }

        try {
            Claims claims = parseClaims(authorization.substring(BEARER_PREFIX.length()));
            String subject = claims.getSubject();
            String role = claims.get(JwtClaims.ROLE, String.class);

            if (!StringUtils.hasText(subject) || !StringUtils.hasText(role)) {
                SecurityContextHolder.clearContext();
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
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

    /**
     * Xác minh chữ ký, thời hạn và trả về payload claims của JWT.
     *
     * @param token chuỗi JWT không gồm tiền tố Bearer
     * @return claims đã được JJWT xác minh
     * @throws JwtException nếu token sai chữ ký, hết hạn hoặc không đúng cấu trúc JWT
     * @throws IllegalArgumentException nếu chuỗi token không hợp lệ
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
