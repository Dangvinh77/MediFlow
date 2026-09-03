package com.mediflow.pharmacy.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.ApiResponse.ApiError;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.pharmacy.infrastructure.security.JwtAuthFilter;
import com.mediflow.pharmacy.infrastructure.security.JwtProperties;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Cấu hình xác thực và phân quyền HTTP của pharmacy-service.
 *
 * <p>Security chain hoạt động theo mô hình stateless: mỗi request phải mang JWT hợp lệ, server không
 * tạo HTTP session và không sử dụng form login hay HTTP Basic. {@link JwtAuthFilter} xác thực token;
 * các annotation {@code @PreAuthorize} tại controller quyết định role nào được gọi từng endpoint.</p>
 *
 * <p>Chỉ health check và tài liệu OpenAPI được truy cập không cần token. Mọi đường dẫn khác mặc định
 * yêu cầu authentication. Cấu hình này là lớp bảo vệ nội bộ bổ sung cho việc xác minh JWT tại
 * gateway (defense in depth).</p>
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    /**
     * Ngăn Spring Boot đăng ký {@link JwtAuthFilter} như một servlet filter độc lập.
     *
     * <p>{@code JwtAuthFilter} là một Spring bean nên Boot có thể tự động thêm nó vào servlet filter
     * chain. Tuy nhiên filter đã được chèn thủ công vào Spring Security chain trong
     * {@link #securityFilterChain(HttpSecurity, JwtAuthFilter, ObjectMapper)}. Tắt registration mặc
     * định bảo đảm filter chỉ chạy một lần và đúng thứ tự trước
     * {@link UsernamePasswordAuthenticationFilter}.</p>
     *
     * @param jwtAuthFilter filter JWT do Spring quản lý
     * @return registration bean bị vô hiệu hóa cho servlet container
     */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(
            JwtAuthFilter jwtAuthFilter) {

        FilterRegistrationBean<JwtAuthFilter> registration =
                new FilterRegistrationBean<>(jwtAuthFilter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Tạo security filter chain cho REST API của pharmacy-service.
     *
     * <ul>
     *   <li>Tắt CSRF vì API không dùng cookie/session để xác thực.</li>
     *   <li>Tắt form login và HTTP Basic để chỉ chấp nhận Bearer JWT.</li>
     *   <li>Dùng {@link SessionCreationPolicy#STATELESS} để không lưu SecurityContext trong session.</li>
     *   <li>Cho phép health check và OpenAPI; các request khác phải được xác thực.</li>
     *   <li>Đăng ký {@link JwtAuthFilter} trước filter username/password mặc định.</li>
     * </ul>
     *
     * @param http builder cấu hình Spring Security
     * @param jwtAuthFilter filter xác minh Bearer JWT
     * @param objectMapper mapper dùng để ghi lỗi 401/403 theo {@link ApiResponse}
     * @return security filter chain đã cấu hình
     * @throws Exception nếu Spring Security không thể dựng filter chain
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthFilter jwtAuthFilter,
            ObjectMapper objectMapper) throws Exception {

        return http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(
                                "/actuator/health",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(
                                        request,
                                        response,
                                        objectMapper,
                                        HttpServletResponse.SC_UNAUTHORIZED,
                                        "UNAUTHORIZED",
                                        "Token không hợp lệ hoặc bị thiếu"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(
                                        request,
                                        response,
                                        objectMapper,
                                        HttpServletResponse.SC_FORBIDDEN,
                                        "FORBIDDEN",
                                        "Bạn không có quyền thực hiện thao tác này")))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Ghi lỗi bảo mật theo response envelope chuẩn của MediFlow.
     *
     * <p>Lỗi xác thực xảy ra trong security filter chain, trước khi request đi vào controller, nên
     * không được xử lý bởi {@code GlobalExceptionHandler}. Phương thức này đảm bảo lỗi 401/403 vẫn có
     * cùng cấu trúc JSON với lỗi nghiệp vụ. Correlation id do gateway truyền xuống được giữ lại nếu
     * request có header tương ứng.</p>
     *
     * @param request request bị từ chối
     * @param response response sẽ được ghi JSON
     * @param objectMapper mapper chuyển response envelope thành JSON
     * @param status HTTP status cần trả về
     * @param code mã lỗi ổn định cho client
     * @param message thông báo lỗi dành cho người dùng
     * @throws IOException nếu không thể ghi JSON vào response stream
     */
    private void writeError(
            HttpServletRequest request,
            HttpServletResponse response,
            ObjectMapper objectMapper,
            int status,
            String code,
            String message) throws IOException {

        String correlationId = request.getHeader(JwtClaims.HEADER_CORRELATION_ID);
        ApiResponse<Void> body = ApiResponse.fail(
                ApiError.of(code, message),
                correlationId);

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
