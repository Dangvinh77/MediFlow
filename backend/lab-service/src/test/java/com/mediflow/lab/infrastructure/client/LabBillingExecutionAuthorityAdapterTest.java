package com.mediflow.lab.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.lab.application.exception.LabUpstreamUnavailableException;
import com.mediflow.lab.domain.exception.LabRuleException;
import com.mediflow.lab.domain.model.CareEpisodeType;
import com.mediflow.lab.domain.model.ClearancePurpose;
import com.mediflow.lab.domain.model.LabFinancialClearance;
import com.mediflow.lab.infrastructure.security.JwtProperties;
import com.mediflow.lab.application.service.LabApplicationService;
import com.mediflow.lab.application.dto.command.FinancialClearanceCommand;
import com.mediflow.lab.application.dto.request.AddResultRequest;
import com.mediflow.lab.application.dto.request.LabResultItem;
import com.mediflow.lab.application.mapper.LabTestDtoMapper;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContext;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContextPort;
import com.mediflow.lab.application.port.out.CorrelationIdProvider;
import com.mediflow.lab.application.port.out.LabClearanceRepositoryPort;
import com.mediflow.lab.application.port.out.LabEmergencyOverrideRepositoryPort;
import com.mediflow.lab.application.port.out.LabEventPublisherPort;
import com.mediflow.lab.application.port.out.LabOutboxPort;
import com.mediflow.lab.application.port.out.LabTestRepositoryPort;
import com.mediflow.lab.domain.model.LabTest;
import com.mediflow.lab.domain.model.LabTestStatus;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
        adapter.start(grant, grant.testId(), CORRELATION);
        verify(client, org.mockito.Mockito.times(2)).start(org.mockito.ArgumentMatchers.eq(grant.clearanceId()),
                org.mockito.ArgumentMatchers.eq(new LabBillingExecutionStartRequest(grant.testId(), "LAB_TEST",
                        grant.testId(), grant.patientId(), "OUTPATIENT_VISIT", grant.careEpisodeId())),
                any(), org.mockito.ArgumentMatchers.eq(CORRELATION));
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
        verifyLookup(grant);
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
        verifyLookup(grant);
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
        verifyLookup(grant);
    }

    @Test
    void requireCurrent_failsClosedWhenBillingResponseIsMalformed() {
        LabFinancialClearance grant = grant();
        when(client.lookup(any(), any(), any())).thenReturn(response(CORRELATION, "{}"));

        assertThatThrownBy(() -> adapter.requireCurrent(grant, grant.testId(), CORRELATION))
                .isInstanceOf(LabUpstreamUnavailableException.class);
        verifyLookup(grant);
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
        verify(client).start(any(), any(), any(), any());
    }

    @Test
    void wrongBusinessTestIsDeniedBeforeHttpEvenWhenItEqualsProjectionId() {
        LabFinancialClearance grant = grant();
        for (UUID wrong : List.of(grant.clearanceTargetId(),
                UUID.fromString("00000000-0000-0000-0000-000000000099"))) {
            assertThatThrownBy(() -> adapter.start(grant, wrong, CORRELATION))
                    .isInstanceOf(LabRuleException.class);
            assertThatThrownBy(() -> adapter.requireCurrent(grant, wrong, CORRELATION))
                    .isInstanceOf(LabRuleException.class);
        }
        verifyNoInteractions(client);
    }

    @Test
    void equalProjectionAndTestIdsRemainValid() {
        LabFinancialClearance original = grant();
        LabFinancialClearance sameId = withProjectionId(original, original.testId());
        when(client.lookup(any(), any(), any())).thenReturn(response(CORRELATION, lookup(sameId, true)));
        adapter.requireCurrent(sameId, sameId.testId(), CORRELATION);
        verifyLookup(sameId);
    }

    @Test
    void absentProjectionIdIsDeniedBeforeHttp() {
        LabFinancialClearance malformed = withProjectionId(grant(), null);
        assertThatThrownBy(() -> adapter.start(malformed, malformed.testId(), CORRELATION))
                .isInstanceOf(LabRuleException.class);
        assertThatThrownBy(() -> adapter.requireCurrent(malformed, malformed.testId(), CORRELATION))
                .isInstanceOf(LabRuleException.class);
        verifyNoInteractions(client);
    }

    @Test
    void requireCurrent_rejectsDifferentOriginalGrantTime() {
        LabFinancialClearance g = grant();
        String body = lookup(g, true).replace(g.grantedAt().toString(), g.grantedAt().plusNanos(1000).toString());
        when(client.lookup(any(), any(), any())).thenReturn(response(CORRELATION, body));
        assertThatThrownBy(() -> adapter.requireCurrent(g, g.testId(), CORRELATION))
                .isInstanceOf(LabRuleException.class);
        verifyLookup(g);
    }

    private static LabFinancialClearance withProjectionId(LabFinancialClearance grant, UUID projectionId) {
        return new LabFinancialClearance(projectionId, grant.clearanceId(), grant.eventId(), grant.invoiceId(),
                grant.accountId(), grant.testId(), grant.patientId(), grant.careEpisodeType(), grant.careEpisodeId(),
                grant.purpose(), grant.amount(), grant.currency(), grant.expiresAt(), grant.emergencyOverride(),
                grant.grantedAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"invoiceId", "accountId", "patientId", "careEpisodeId"})
    void requireCurrent_rejectsMismatchedBillingContext(String field) {
        LabFinancialClearance grant = grant();
        String body = lookup(grant, true).replaceAll("\"" + field + "\":\"[^\"]+\"",
                "\"" + field + "\":\"00000000-0000-0000-0000-000000000099\"");
        when(client.lookup(any(), any(), any())).thenReturn(response(CORRELATION, body));
        assertThatThrownBy(() -> adapter.requireCurrent(grant, grant.testId(), CORRELATION))
                .isInstanceOf(LabRuleException.class);
        verifyLookup(grant);
    }

    @Test
    void requireCurrent_rejectsExpiredGrantAfterLookup() {
        LabFinancialClearance original = grant();
        LabFinancialClearance expired = new LabFinancialClearance(original.clearanceTargetId(),
                original.clearanceId(), original.eventId(), original.invoiceId(), original.accountId(),
                original.testId(), original.patientId(), original.careEpisodeType(), original.careEpisodeId(),
                original.purpose(), original.amount(), original.currency(), NOW, false, original.grantedAt());
        when(client.lookup(any(), any(), any())).thenReturn(response(CORRELATION, lookup(expired, true)));
        assertThatThrownBy(() -> adapter.requireCurrent(expired, expired.testId(), CORRELATION))
                .isInstanceOf(LabRuleException.class);
        verifyLookup(expired);
    }

    @Test
    void requireCurrent_rejectsStaleObservation() {
        LabFinancialClearance grant = grant();
        String body = lookup(grant, true).replace("\"observedAt\":\"" + NOW + "\"",
                "\"observedAt\":\"" + NOW.minusSeconds(6) + "\"");
        when(client.lookup(any(), any(), any())).thenReturn(response(CORRELATION, body));
        assertThatThrownBy(() -> adapter.requireCurrent(grant, grant.testId(), CORRELATION))
                .isInstanceOf(LabUpstreamUnavailableException.class);
        verifyLookup(grant);
    }

    private void verifyLookup(LabFinancialClearance grant) {
        verify(client).lookup(org.mockito.ArgumentMatchers.eq(grant.clearanceId()),
                org.mockito.ArgumentMatchers.startsWith("Bearer "), org.mockito.ArgumentMatchers.eq(CORRELATION));
    }

    @Test
    void consumerProjectionCanStartAndCompleteThroughRealExecutionAdapter() {
        var tests = mock(LabTestRepositoryPort.class);
        var clearances = mock(LabClearanceRepositoryPort.class);
        var correlations = mock(CorrelationIdProvider.class);
        var staff = mock(AuthenticatedStaffContextPort.class);
        var outbox = mock(LabOutboxPort.class);
        var service = new LabApplicationService(tests, mock(LabEventPublisherPort.class),
                mock(LabTestDtoMapper.class), correlations, outbox, clearances,
                mock(LabEmergencyOverrideRepositoryPort.class), staff, true, true, CLOCK, null, null, adapter);
        var date = java.time.LocalDate.of(2026, 10, 21);
        LabTest test = LabTest.createV2(UUID.randomUUID(), grant().patientId(), UUID.randomUUID(),
                UUID.randomUUID(), CareEpisodeType.OUTPATIENT_VISIT, grant().careEpisodeId(), "CBC", "LAB-CBC", date);
        when(tests.findByIdForUpdate(test.getTestId())).thenReturn(java.util.Optional.of(test));
        when(tests.save(any())).thenAnswer(call -> call.getArgument(0));
        when(clearances.claimAndSave(any(), any())).thenReturn(true);
        when(correlations.currentOrCreate()).thenReturn(UUID.fromString(CORRELATION));
        when(staff.currentStaff()).thenReturn(java.util.Optional.of(
                new AuthenticatedStaffContext(UUID.randomUUID(), "LAB_TECH")));
        var original = grant();
        service.onFinancialClearance(new FinancialClearanceCommand(original.eventId(), "financial.clearance.granted",
                1, original.grantedAt(), CORRELATION, "billing-service", original.clearanceId(), original.invoiceId(),
                original.accountId(), test.getPatientId(), test.getCareEpisodeType(), test.getCareEpisodeId(),
                ClearancePurpose.LAB_TEST, List.of(test.getTestId()), original.amount(), "VND", original.expiresAt(), false));
        ArgumentCaptor<List<LabFinancialClearance>> captured = ArgumentCaptor.forClass(List.class);
        verify(clearances).claimAndSave(org.mockito.ArgumentMatchers.eq(original.eventId()), captured.capture());
        LabFinancialClearance projection = captured.getValue().getFirst();
        assertThat(projection.testId()).isEqualTo(test.getTestId());
        assertThat(projection.clearanceTargetId()).isNotNull();
        assertThat(test.getStatus()).isEqualTo(LabTestStatus.READY);
        when(clearances.findValidByTestId(test.getTestId(), NOW)).thenReturn(java.util.Optional.of(projection));
        when(clearances.findByTestIdAndClearanceId(test.getTestId(), projection.clearanceId()))
                .thenReturn(java.util.Optional.of(projection));
        when(client.start(any(), any(), any(), any())).thenReturn(response(CORRELATION, """
                {"operationId":"%s","clearanceId":"%s","sourceType":"LAB_TEST","sourceId":"%s","state":"STARTED","startedAt":"%s"}
                """.formatted(test.getTestId(), projection.clearanceId(), test.getTestId(), NOW)));
        when(client.lookup(any(), any(), any())).thenReturn(response(CORRELATION, lookup(projection, true)));

        service.start(test.getTestId(), null);
        assertThat(test.getStatus()).isEqualTo(LabTestStatus.IN_PROGRESS);
        assertThatThrownBy(() -> service.start(test.getTestId(), null)).isInstanceOf(LabRuleException.class);
        service.addResults(test.getTestId(), new AddResultRequest(
                List.of(new LabResultItem("Hemoglobin", "13.2", "g/dL", "12-16")), "Normal", date));

        assertThat(test.getStatus()).isEqualTo(LabTestStatus.COMPLETED);
        verify(client).start(org.mockito.ArgumentMatchers.eq(projection.clearanceId()),
                org.mockito.ArgumentMatchers.eq(new LabBillingExecutionStartRequest(test.getTestId(), "LAB_TEST",
                        test.getTestId(), test.getPatientId(), "OUTPATIENT_VISIT", test.getCareEpisodeId())),
                any(), org.mockito.ArgumentMatchers.eq(CORRELATION));
        verifyLookup(projection);
        verify(outbox).append(any());
    }

    private static LabFinancialClearance grant() {
        UUID testId = UUID.fromString("00000000-0000-0000-0000-000000000041");
        return LabFinancialClearance.grant(UUID.fromString("00000000-0000-0000-0000-000000000040"),
                UUID.fromString("00000000-0000-0000-0000-000000000042"),
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
