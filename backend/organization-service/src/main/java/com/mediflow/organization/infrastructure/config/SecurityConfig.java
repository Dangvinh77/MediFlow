package com.mediflow.organization.infrastructure.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.ApiResponse.ApiError;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.infrastructure.security.JwtAuthFilter;
import com.mediflow.organization.infrastructure.security.JwtProperties;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

/** Stateless JWT security for organization-service HTTP endpoints. */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtProperties properties) {
        return new JwtAuthFilter(properties);
    }

    /** The filter is inserted by Spring Security, not registered as a second servlet filter. */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(
            JwtAuthFilter jwtAuthFilter) {
        FilterRegistrationBean<JwtAuthFilter> registration =
                new FilterRegistrationBean<>(jwtAuthFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthFilter jwtAuthFilter,
            ObjectMapper objectMapper,
            CorrelationIdProvider correlationIds) throws Exception {

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
                                "/actuator/info",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(
                                        response,
                                        objectMapper,
                                        correlationIds,
                                        HttpServletResponse.SC_UNAUTHORIZED,
                                        "UNAUTHORIZED",
                                        "Token không hợp lệ hoặc bị thiếu"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(
                                        response,
                                        objectMapper,
                                        correlationIds,
                                        HttpServletResponse.SC_FORBIDDEN,
                                        "FORBIDDEN",
                                        "Bạn không có quyền thực hiện thao tác này")))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private void writeError(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            CorrelationIdProvider correlationIds,
            int status,
            String code,
            String message) throws IOException {

        String correlationId = correlationIds.currentOrCreate().toString();
        ApiResponse<Void> body = ApiResponse.fail(
                ApiError.of(code, message), correlationId);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(JwtClaims.HEADER_CORRELATION_ID, correlationId);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
