package com.mediflow.lab.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.lab.application.exception.LabUpstreamUnavailableException;
import com.mediflow.lab.domain.exception.LabRuleException;
import com.mediflow.lab.domain.model.CareEpisodeType;
import com.mediflow.lab.domain.model.ClearancePurpose;
import com.mediflow.lab.domain.model.LabFinancialClearance;
import com.mediflow.lab.infrastructure.security.JwtProperties;
import feign.Request;
import feign.Response;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class LabBillingExecutionAuthorityAdapterTest {
    private static final Instant NOW = Instant.parse("2026-10-21T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String CORRELATION = "d8a936b4-4d75-4c62-9d58-fd540da89be0";
    private static final String SECRET = "test-secret-must-have-at-least-32-bytes";

    private final LabBillingClearanceFeignClient client = mock(LabBillingClearanceFeignClient.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final CircuitBreaker breaker = CircuitBreaker.ofDefaults("lab-billing-adapter-test");
    private final LabBillingExecutionAuthorityAdapter adapter = new LabBillingExecutionAuthorityAdapter(client,
            mapper, CLOCK, new JwtProperties(SECRET), breaker);

    @Test
    void start_sendsStableExactTestIdentityAndAcceptsOnlyMatchingDurableReceipt() {
        LabFinancialClearance grant = grant();
        UUID testId = grant.testId();
        when(client.start(any(), any(), any(), any())).thenReturn(response(CORRELATION, """
                {"operationId":"%s","clearanceId":"%s","sourceType":"LAB_TEST","sourceId":"%s","state":"STARTED","startedAt":"%s"}
                """.formatted(testId, grant.clearanceId(), testId, NOW)));

        adapter.start(grant, testId, CORRELATION);

        ArgumentCaptor<LabBillingExecutionStartRequest> request = ArgumentCaptor.forClass(
                LabBillingExecutionStartRequest.class);
        ArgumentCaptor<String> authorization = ArgumentCaptor.forClass(String.class);
        verify(client).start(org.mockito.ArgumentMatchers.eq(grant.clearanceId()), request.capture(),
                authorization.capture(), org.mockito.ArgumentMatchers.eq(CORRELATION));
        assertThat(request.getValue()).isEqualTo(new LabBillingExecutionStartRequest(testId, "LAB_TEST", testId,
                grant.patientId(), "OUTPATIENT_VISIT", grant.careEpisodeId()));
        String token = authorization.getValue().substring("Bearer ".length());
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser().verifyWith(key).clock(() -> java.util.Date.from(NOW)).build()
                .parseSignedClaims(token).getPayload();
        assertThat(claims.getSubject()).isEqualTo("lab-service");
        assertThat(claims.get(JwtClaims.ROLE, String.class)).isEqualTo("SYSTEM");
        assertThat(claims.get(JwtClaims.TYPE, String.class)).isEqualTo(JwtClaims.SERVICE_TOKEN_TYPE);
    }

    @Test
    void start_acceptsOriginalReceiptTimestampOnAnIdempotentRetry() {
        LabFinancialClearance grant = grant();
        Instant originalStart = NOW.minusSeconds(600);
        when(client.start(any(), any(), any(), any())).thenReturn(response(CORRELATION, """
                {"operationId":"%s","clearanceId":"%s","sourceType":"LAB_TEST","sourceId":"%s","state":"STARTED","startedAt":"%s"}
                """.formatted(grant.testId(), grant.clearanceId(), grant.testId(), originalStart)));

        adapter.start(grant, grant.testId(), CORRELATION);
    }

    @Test
    void requireCurrent_rejectsClearanceAfterBillingRevokesIt() {
        LabFinancialClearance grant = grant();
        when(client.lookup(any(), any(), any())).thenReturn(response(CORRELATION, lookup(grant, false)));

        assertThatThrownBy(() -> adapter.requireCurrent(grant, grant.testId(), CORRELATION))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_CLEARANCE_REQUIRED");
    }

    @Test
    void requireCurrent_rejectsEligibleClearanceForDifferentExactSource() {
        LabFinancialClearance grant = grant();
        String wrongTarget = lookup(grant, true).replace("\"sourceId\":\"" + grant.testId() + "\"",
                "\"sourceId\":\"00000000-0000-0000-0000-000000000099\"");
        when(client.lookup(any(), any(), any())).thenReturn(response(CORRELATION, wrongTarget));

        assertThatThrownBy(() -> adapter.requireCurrent(grant, grant.testId(), CORRELATION))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_CLEARANCE_REQUIRED");
    }

    @Test
    void requireCurrent_mapsBillingConflictToExactTargetDenial() {
        LabFinancialClearance grant = grant();
        Request request = Request.create(Request.HttpMethod.GET, "http://billing.invalid", Map.of(), null,
                StandardCharsets.UTF_8, null);
        Response conflict = Response.builder().status(409).reason("Conflict").request(request)
                .headers(Map.of()).body("{}".getBytes(StandardCharsets.UTF_8)).build();
        when(client.lookup(any(), any(), any()))
                .thenThrow(FeignException.errorStatus("clearance-lookup", conflict));

        assertThatThrownBy(() -> adapter.requireCurrent(grant, grant.testId(), CORRELATION))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_CLEARANCE_REQUIRED");
    }

    @Test
    void requireCurrent_failsClosedWhenBillingResponseIsMalformed() {
        LabFinancialClearance grant = grant();
        when(client.lookup(any(), any(), any())).thenReturn(response(CORRELATION, "{}"));

        assertThatThrownBy(() -> adapter.requireCurrent(grant, grant.testId(), CORRELATION))
                .isInstanceOf(LabUpstreamUnavailableException.class);
    }

    @Test
    void requireCurrent_acceptsOnlyExactCurrentOutpatientGrant() {
        LabFinancialClearance grant = grant();
        when(client.lookup(any(), any(), any())).thenReturn(response(CORRELATION, lookup(grant, true)));

        adapter.requireCurrent(grant, grant.testId(), CORRELATION);

        verify(client).lookup(org.mockito.ArgumentMatchers.eq(grant.clearanceId()),
                org.mockito.ArgumentMatchers.startsWith("Bearer "), org.mockito.ArgumentMatchers.eq(CORRELATION));
    }

    @Test
    void start_refusesAReceiptForADifferentTest() {
        LabFinancialClearance grant = grant();
        UUID other = UUID.randomUUID();
        when(client.start(any(), any(), any(), any())).thenReturn(response(CORRELATION, """
                {"operationId":"%s","clearanceId":"%s","sourceType":"LAB_TEST","sourceId":"%s","state":"STARTED","startedAt":"%s"}
                """.formatted(other, grant.clearanceId(), other, NOW)));

        assertThatThrownBy(() -> adapter.start(grant, grant.testId(), CORRELATION))
                .isInstanceOf(LabUpstreamUnavailableException.class);
    }

    private static LabFinancialClearance grant() {
        UUID testId = UUID.fromString("00000000-0000-0000-0000-000000000041");
        return LabFinancialClearance.grant(testId, UUID.fromString("00000000-0000-0000-0000-000000000042"),
                UUID.fromString("00000000-0000-0000-0000-000000000043"),
                UUID.fromString("00000000-0000-0000-0000-000000000044"),
                UUID.fromString("00000000-0000-0000-0000-000000000045"), testId,
                UUID.fromString("00000000-0000-0000-0000-000000000046"), CareEpisodeType.OUTPATIENT_VISIT,
                UUID.fromString("00000000-0000-0000-0000-000000000047"), ClearancePurpose.LAB_TEST,
                new BigDecimal("250000.00"), "VND", NOW.plusSeconds(600), false, NOW.minusSeconds(30));
    }

    private static String lookup(LabFinancialClearance grant, boolean eligible) {
        return """
                {"exists":true,"clearanceId":"%s","eligible":%s,"invoiceId":"%s","accountId":"%s","patientId":"%s","purpose":"LAB_TEST","sourceType":"LAB_TEST","sourceId":"%s","careEpisodeType":"OUTPATIENT_VISIT","careEpisodeId":"%s","admissionId":null,"surgeryCaseId":null,"grantedAt":"%s","expiresAt":"%s","observedAt":"%s"}
                """.formatted(grant.clearanceId(), eligible, grant.invoiceId(), grant.accountId(), grant.patientId(),
                grant.testId(), grant.careEpisodeId(), grant.grantedAt(), grant.expiresAt(), NOW);
    }

    private static ResponseEntity<String> response(String correlation, String data) {
        return ResponseEntity.ok().header("X-Correlation-Id", correlation).body("""
                {"success":true,"data":%s,"error":null,"correlationId":"%s"}
                """.formatted(data.strip(), correlation));
    }
}
