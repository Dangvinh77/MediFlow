package com.mediflow.report.infrastructure.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.ApiResponse.ApiError;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.report.infrastructure.security.JwtAuthFilter;
import com.mediflow.report.infrastructure.security.JwtProperties;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Stateless JWT verification and default-deny authorization for report HTTP endpoints. */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtProperties properties) {
        return new JwtAuthFilter(properties);
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
                                                   ObjectMapper objectMapper,
                                                   @Value("${mediflow.security.swagger-permit:false}")
                                                   boolean swaggerPermit) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    authorize.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    authorize.requestMatchers("/actuator/health").permitAll();
                    if (swaggerPermit) {
                        authorize.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                                .permitAll();
                    } else {
                        authorize.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                                .denyAll();
                    }
                    authorize.anyRequest().authenticated();
                })
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeError(
                                request, response, objectMapper, HttpServletResponse.SC_UNAUTHORIZED,
                                "UNAUTHORIZED", "Token không hợp lệ hoặc bị thiếu"))
                        .accessDeniedHandler((request, response, exception) -> writeError(
                                request, response, objectMapper, HttpServletResponse.SC_FORBIDDEN,
                                "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này")))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response,
                            ObjectMapper objectMapper, int status, String code, String message)
            throws IOException {
        ApiResponse<Void> body = ApiResponse.fail(
                ApiError.of(code, message), request.getHeader(JwtClaims.HEADER_CORRELATION_ID));
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
