package com.mediflow.patient.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.ApiResponse.ApiError;
import com.mediflow.patient.application.port.out.CorrelationIdProvider;
import com.mediflow.patient.infrastructure.security.JwtAuthFilter;
import com.mediflow.patient.infrastructure.security.JwtProperties;
import jakarta.servlet.DispatcherType;
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

import java.nio.charset.StandardCharsets;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {
    @Bean JwtAuthFilter jwtAuthFilter(JwtProperties properties) { return new JwtAuthFilter(properties); }
    @Bean FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setEnabled(false); return bean;
    }
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
                                            ObjectMapper mapper, CorrelationIdProvider ids) throws Exception {
        return http.csrf(c -> c.disable()).formLogin(c -> c.disable()).httpBasic(c -> c.disable())
                .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(c -> c.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info", "/swagger-ui.html",
                                "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(c -> c.authenticationEntryPoint((req, res, ex) -> write(res, mapper, ids,
                                401, "UNAUTHORIZED", "Token không hợp lệ hoặc bị thiếu"))
                        .accessDeniedHandler((req, res, ex) -> write(res, mapper, ids,
                                403, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này")))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class).build();
    }
    private void write(HttpServletResponse response, ObjectMapper mapper, CorrelationIdProvider ids,
                       int status, String code, String message) throws java.io.IOException {
        response.setStatus(status); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getOutputStream(), ApiResponse.fail(ApiError.of(code, message),
                ids.currentOrCreate().toString()));
    }
}
