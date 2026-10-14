package com.mediflow.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.ApiResponse.ApiError;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.infrastructure.security.JwtAuthFilter;
import com.mediflow.organization.infrastructure.security.JwtProperties;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.MediaType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Supplier;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, com.mediflow.patient.infrastructure.security.JwtProperties.class})
public class CoreSecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtAuthFilter organizationJwtAuthFilter(JwtProperties properties) {
        return new JwtAuthFilter(properties);
    }

    @Bean
    FilterRegistrationBean<JwtAuthFilter> organizationJwtAuthFilterRegistration(
            JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    com.mediflow.patient.infrastructure.security.JwtAuthFilter patientJwtAuthFilter(
            com.mediflow.patient.infrastructure.security.JwtProperties properties) {
        return new com.mediflow.patient.infrastructure.security.JwtAuthFilter(properties);
    }

    @Bean
    FilterRegistrationBean<com.mediflow.patient.infrastructure.security.JwtAuthFilter>
    patientJwtAuthFilterRegistration(
            com.mediflow.patient.infrastructure.security.JwtAuthFilter filter) {
        FilterRegistrationBean<com.mediflow.patient.infrastructure.security.JwtAuthFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Order(1)
    SecurityFilterChain organizationSecurityChain(
            HttpSecurity http, JwtAuthFilter filter, ObjectMapper mapper,
            CorrelationIdProvider ids) throws Exception {
        return authenticatedChain(http, "/api/v1/org/**", filter, mapper, ids::currentOrCreate);
    }

    @Bean
    @Order(2)
    SecurityFilterChain patientSecurityChain(
            HttpSecurity http,
            com.mediflow.patient.infrastructure.security.JwtAuthFilter filter,
            ObjectMapper mapper,
            com.mediflow.patient.application.port.out.CorrelationIdProvider ids) throws Exception {
        return authenticatedChain(http, "/api/v1/patients/**", filter, mapper, ids::currentOrCreate);
    }

    @Bean
    @Order(3)
    SecurityFilterChain defaultDenyChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/**").csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info", "/swagger-ui.html",
                                "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().denyAll()).build();
    }

    private SecurityFilterChain authenticatedChain(
            HttpSecurity http, String path, jakarta.servlet.Filter filter,
            ObjectMapper mapper, Supplier<UUID> ids) throws Exception {
        return http.securityMatcher(path).csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeError(
                                response, mapper, ids, HttpServletResponse.SC_UNAUTHORIZED,
                                "UNAUTHORIZED", "Token không hợp lệ hoặc bị thiếu"))
                        .accessDeniedHandler((request, response, exception) -> writeError(
                                response, mapper, ids, HttpServletResponse.SC_FORBIDDEN,
                                "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này")))
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class).build();
    }

    private void writeError(HttpServletResponse response, ObjectMapper mapper,
                            Supplier<UUID> ids, int status, String code,
                            String message) throws IOException {
        String correlationId = ids.get().toString();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(JwtClaims.HEADER_CORRELATION_ID, correlationId);
        mapper.writeValue(response.getOutputStream(), ApiResponse.fail(
                ApiError.of(code, message), correlationId));
    }
}
