package com.mediflow.gateway.auth;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.gateway.filter.CorrelationIdWebFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.bind.support.WebExchangeBindException;

import java.util.List;

/** Stable validation/error envelope for the public authentication endpoints. */
@RestControllerAdvice(assignableTypes = AuthController.class)
public class GatewayAuthExceptionHandler {

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            WebExchangeBindException exception, ServerWebExchange exchange) {
        List<ApiResponse.ErrorDetail> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ApiResponse.ErrorDetail(
                        error.getField(), error.getDefaultMessage()))
                .toList();
        return badRequest(exchange, details);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedInput(
            ServerWebInputException exception, ServerWebExchange exchange) {
        return badRequest(exchange, List.of());
    }

    private ResponseEntity<ApiResponse<Void>> badRequest(
            ServerWebExchange exchange, List<ApiResponse.ErrorDetail> details) {
        String correlationId = exchange.getAttribute(CorrelationIdWebFilter.ATTRIBUTE);
        if (correlationId == null) {
            correlationId = CorrelationIdWebFilter.normalize(
                    exchange.getRequest().getHeaders().getFirst(
                            JwtClaims.HEADER_CORRELATION_ID));
        }
        ApiResponse.ApiError error = new ApiResponse.ApiError(
                "VALIDATION_ERROR", "Request validation failed", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header(JwtClaims.HEADER_CORRELATION_ID, correlationId)
                .body(ApiResponse.fail(error, correlationId));
    }
}
