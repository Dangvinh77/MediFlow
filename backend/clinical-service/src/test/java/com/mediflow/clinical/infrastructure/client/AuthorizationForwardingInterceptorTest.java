package com.mediflow.clinical.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.mediflow.clinical.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.clinical.infrastructure.security.JwtProperties;
import com.mediflow.clinical.infrastructure.web.CorrelationIdFilter;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.common.security.Roles;

import jakarta.servlet.ServletException;
import feign.RequestTemplate;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class AuthorizationForwardingInterceptorTest {

    private static final String SECRET = "test-secret-must-have-at-least-32-bytes";
    private static final Instant NOW = Instant.parse("2026-09-18T00:00:00Z");
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(
            SECRET.getBytes(StandardCharsets.UTF_8));

    private final ThreadLocalCorrelationIdProvider correlationIds = new ThreadLocalCorrelationIdProvider();
    private final AuthorizationForwardingInterceptor interceptor =
            new AuthorizationForwardingInterceptor(
                    correlationIds,
                    new JwtProperties(SECRET),
                    Clock.fixed(NOW, ZoneOffset.UTC));
    private final CorrelationIdFilter correlationFilter = new CorrelationIdFilter(correlationIds);

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
        correlationIds.clear();
    }

    @Test
    void apply_boundCorrelationId_forwardsExactId() {
        UUID expected = UUID.randomUUID();
        correlationIds.bind(expected);

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers().get(JwtClaims.HEADER_CORRELATION_ID))
                .containsExactly(expected.toString());
    }

    @Test
    void apply_withoutWebRequest_generatesAndReusesCorrelationId() {
        RequestTemplate first = new RequestTemplate();
        RequestTemplate second = new RequestTemplate();

        interceptor.apply(first);
        interceptor.apply(second);

        String firstId = first.headers().get(JwtClaims.HEADER_CORRELATION_ID).iterator().next();
        String secondId = second.headers().get(JwtClaims.HEADER_CORRELATION_ID).iterator().next();
        assertThat(UUID.fromString(firstId)).isNotNull();
        assertThat(secondId).isEqualTo(firstId);
    }

    @Test
    void apply_requestHasHumanBearerToken_doesNotForwardHumanCredential() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);
        assertThat(template.headers().get(HttpHeaders.AUTHORIZATION))
                .doesNotContain("Bearer test-token");
        Claims claims = parseServiceClaims(template);
        assertThat(claims.getSubject()).isEqualTo("clinical-service");
        assertThat(claims.get(JwtClaims.ROLE, String.class)).isEqualTo(Roles.SYSTEM);
        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(NOW);
        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plusSeconds(60));
        assertThat(template.headers().get(JwtClaims.HEADER_CORRELATION_ID)).hasSize(1);
    }

    @Test
    void apply_noWebRequest_stillAddsServiceAuthorization() {
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);
        assertThat(template.headers()).containsKey(HttpHeaders.AUTHORIZATION);
        assertThat(parseServiceClaims(template).getSubject()).isEqualTo("clinical-service");
    }

    @Test
    void apply_afterProviderClear_doesNotReusePreviousCorrelationId() {
        UUID firstId = UUID.randomUUID();
        correlationIds.bind(firstId);
        RequestTemplate first = new RequestTemplate();
        interceptor.apply(first);

        correlationIds.clear();
        RequestTemplate second = new RequestTemplate();
        interceptor.apply(second);

        String secondId = second.headers().get(JwtClaims.HEADER_CORRELATION_ID).iterator().next();
        assertThat(UUID.fromString(secondId)).isNotNull();
        assertThat(secondId).isNotEqualTo(firstId.toString());
    }

    @Test
    void apply_circuitBreakerExecutor_reusesRequestCorrelationIdWithoutWorkerProviderLeak() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            UUID firstId = UUID.randomUUID();
            UUID secondId = UUID.randomUUID();

            WorkerObservation first = runRequestWithWorker(executor, firstId);
            WorkerObservation second = runRequestWithWorker(executor, secondId);

            assertThat(first.headerId()).isEqualTo(firstId);
            assertThat(first.workerProviderId()).isNull();
            assertThat(second.headerId()).isEqualTo(secondId);
            assertThat(second.workerProviderId()).isNull();
            assertThat(second.workerThreadId()).isEqualTo(first.workerThreadId());
        } finally {
            executor.shutdownNow();
        }
    }

    private WorkerObservation runRequestWithWorker(ExecutorService executor, UUID expectedId)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(JwtClaims.HEADER_CORRELATION_ID, expectedId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        AtomicReference<WorkerObservation> observation = new AtomicReference<>();
        try {
            correlationFilter.doFilter(request, response, (servletRequest, servletResponse) -> {
                RequestAttributes propagated = RequestContextHolder.getRequestAttributes();
                try {
                    observation.set(executor.submit(() -> applyOnWorker(propagated)).get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new ServletException(exception);
                } catch (ExecutionException exception) {
                    throw new ServletException(exception.getCause());
                }
            });
            return observation.get();
        } finally {
            RequestContextHolder.resetRequestAttributes();
            correlationIds.clear();
        }
    }

    private WorkerObservation applyOnWorker(RequestAttributes propagated) {
        RequestContextHolder.setRequestAttributes(propagated);
        try {
            RequestTemplate template = new RequestTemplate();
            interceptor.apply(template);
            String header = template.headers().get(JwtClaims.HEADER_CORRELATION_ID).iterator().next();
            return new WorkerObservation(
                    UUID.fromString(header), correlationIds.peek(), Thread.currentThread().getId());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private Claims parseServiceClaims(RequestTemplate template) {
        String authorization = template.headers().get(HttpHeaders.AUTHORIZATION).iterator().next();
        assertThat(authorization).startsWith("Bearer ");
        return Jwts.parser()
                .clock(() -> Date.from(NOW))
                .verifyWith(SIGNING_KEY)
                .build()
                .parseSignedClaims(authorization.substring("Bearer ".length()))
                .getPayload();
    }

    private record WorkerObservation(UUID headerId, UUID workerProviderId, long workerThreadId) {
    }
}
