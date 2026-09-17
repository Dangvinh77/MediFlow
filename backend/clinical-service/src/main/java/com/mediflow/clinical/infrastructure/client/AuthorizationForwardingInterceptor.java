package com.mediflow.clinical.infrastructure.client;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.infrastructure.correlation.CorrelationIdRequestAttribute;
import com.mediflow.common.security.JwtClaims;

import feign.RequestInterceptor;
import feign.RequestTemplate;

/** Forwards caller credentials and correlation metadata to internal lookup endpoints. */
@Component
public class AuthorizationForwardingInterceptor implements RequestInterceptor {

    private final CorrelationIdProvider correlationIds;

    public AuthorizationForwardingInterceptor(CorrelationIdProvider correlationIds) {
        this.correlationIds = correlationIds;
    }

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes = currentRequestAttributes();
        UUID correlationId = CorrelationIdRequestAttribute.read(attributes);
        if (correlationId == null) {
            correlationId = correlationIds.currentOrCreate();
        }
        template.header(JwtClaims.HEADER_CORRELATION_ID, correlationId.toString());
        if (attributes != null) {
            String authorization = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && !authorization.isBlank()) {
                template.header(HttpHeaders.AUTHORIZATION, authorization);
            }
        }
    }

    private ServletRequestAttributes currentRequestAttributes() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes;
        }
        return null;
    }
}
