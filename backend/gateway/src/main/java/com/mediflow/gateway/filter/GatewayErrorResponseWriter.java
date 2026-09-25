package com.mediflow.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/** Writes Gateway failures using the repository-wide ApiResponse envelope. */
@Component
public class GatewayErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public GatewayErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(
            ServerWebExchange exchange,
            HttpStatus status,
            String code,
            String message) {
        if (exchange.getResponse().isCommitted()) {
            return exchange.getResponse().setComplete();
        }

        String correlationId = exchange.getAttribute(CorrelationIdWebFilter.ATTRIBUTE);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = exchange.getRequest().getHeaders()
                    .getFirst(JwtClaims.HEADER_CORRELATION_ID);
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = CorrelationIdWebFilter.normalize(null);
        }
        ApiResponse<Void> body = ApiResponse.fail(
                ApiResponse.ApiError.of(code, message), correlationId);
        byte[] payload;
        try {
            payload = objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException exception) {
            payload = ("{\"success\":false,\"data\":null,\"error\":{"
                    + "\"code\":\"GATEWAY_ERROR\",\"message\":\"Gateway error\",\"details\":[]}}")
                    .getBytes(StandardCharsets.UTF_8);
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().setContentLength(payload.length);
        exchange.getResponse().getHeaders().set(JwtClaims.HEADER_CORRELATION_ID, correlationId);
        return exchange.getResponse().writeWith(Mono.just(
                exchange.getResponse().bufferFactory().wrap(payload)));
    }
}
