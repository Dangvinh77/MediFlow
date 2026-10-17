package com.mediflow.support.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.ApiResponse.ApiError;
import com.mediflow.common.security.JwtClaims;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** Module chains authenticate their own paths; everything else is denied by the host. */
@Configuration(proxyBeanMethods = false)
public class SupportSecurityConfiguration {
    @Bean
    @Order(100)
    SecurityFilterChain supportFallbackSecurityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable()).logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health").permitAll().anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setStatus(403);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            mapper.writeValue(response.getOutputStream(), ApiResponse.fail(
                                    ApiError.of("FORBIDDEN", "Access denied"), request.getHeader(JwtClaims.HEADER_CORRELATION_ID)));
                        })
                        .accessDeniedHandler((request, response, ex) -> {
                            response.setStatus(403);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            mapper.writeValue(response.getOutputStream(), ApiResponse.fail(
                                    ApiError.of("FORBIDDEN", "Access denied"), request.getHeader(JwtClaims.HEADER_CORRELATION_ID)));
                        }))
                .build();
    }
}
