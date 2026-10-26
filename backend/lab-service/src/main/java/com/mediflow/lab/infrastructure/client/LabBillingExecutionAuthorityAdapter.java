package com.mediflow.lab.infrastructure.client;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.lab.application.exception.LabUpstreamUnavailableException;
import com.mediflow.lab.application.port.out.LabExecutionAuthorityPort;
import com.mediflow.lab.domain.exception.LabRuleException;
import com.mediflow.lab.domain.model.CareEpisodeType;
import com.mediflow.lab.domain.model.ClearancePurpose;
import com.mediflow.lab.domain.model.LabFinancialClearance;
import com.mediflow.lab.infrastructure.security.JwtProperties;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.http.ResponseEntity;

/**
 * Checks current Billing ledger authority and obtains an account-locked durable start receipt.
 * A stale local clearance is never sufficient to start work or accept results.
 */
public final class LabBillingExecutionAuthorityAdapter implements LabExecutionAuthorityPort {
    private static final int MAX_BODY_BYTES = 16_384;
    private static final String PURPOSE = "LAB_TEST";
    private static final String SOURCE_TYPE = "LAB_TEST";
    private static final String EPISODE_TYPE = "OUTPATIENT_VISIT";

    private final LabBillingClearanceFeignClient client;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final SecretKey signingKey;
    private final CircuitBreaker circuitBreaker;

    public LabBillingExecutionAuthorityAdapter(LabBillingClearanceFeignClient client, ObjectMapper mapper,
            Clock clock, JwtProperties jwt, CircuitBreaker circuitBreaker) {
        this.client = client;
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.clock = clock;
        this.signingKey = Keys.hmacShaKeyFor(jwt.secret().getBytes(StandardCharsets.UTF_8));
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public void start(LabFinancialClearance clearance, UUID testId, String correlationId) {
        requireExactLocalTarget(clearance, testId);
        requireCorrelation(correlationId);
        LabBillingExecutionStartRequest request = new LabBillingExecutionStartRequest(testId, SOURCE_TYPE,
                testId, clearance.patientId(), EPISODE_TYPE, clearance.careEpisodeId());
        ResponseEntity<String> response;
        try {
            response = circuitBreaker.executeSupplier(() -> client.start(clearance.clearanceId(), request,
                    serviceToken(), correlationId));
        } catch (FeignException denied) {
            if (denied.status() == 404 || denied.status() == 409) throw clearanceRequired();
            throw new LabUpstreamUnavailableException();
        } catch (RuntimeException unavailable) {
            throw new LabUpstreamUnavailableException();
        }
        try {
            verifyStartReceipt(clearance, testId, correlationId, response);
        } catch (Exception malformed) {
            throw new LabUpstreamUnavailableException();
        }
    }

    @Override
    public void requireCurrent(LabFinancialClearance clearance, UUID testId, String correlationId) {
        requireExactLocalTarget(clearance, testId);
        requireCorrelation(correlationId);
        ResponseEntity<String> response;
        try {
            response = circuitBreaker.executeSupplier(() -> client.lookup(clearance.clearanceId(), serviceToken(), correlationId));
        } catch (FeignException denied) {
            if (denied.status() == 404 || denied.status() == 409) throw clearanceRequired();
            throw new LabUpstreamUnavailableException();
        } catch (RuntimeException unavailable) {
            throw new LabUpstreamUnavailableException();
        }
        try {
            verifyCurrentLookup(clearance, testId, correlationId, response);
        } catch (LabRuleException denied) {
            throw denied;
        } catch (Exception malformed) {
            throw new LabUpstreamUnavailableException();
        }
    }

    private void verifyCurrentLookup(LabFinancialClearance grant, UUID testId, String correlation,
            ResponseEntity<String> response) throws Exception {
        JsonNode root = responseRoot(response, correlation);
        JsonNode data = root.get("data");
        if (data == null || !data.isObject() || !grant.clearanceId().equals(uuid(data, "clearanceId"))) {
            throw new IllegalArgumentException("Billing returned a different clearance");
        }
        boolean exists = bool(data, "exists");
        boolean eligible = bool(data, "eligible");
        Instant observedAt = instant(data, "observedAt");
        Instant now = clock.instant();
        if (observedAt.isBefore(now.minusSeconds(5)) || observedAt.isAfter(now.plusSeconds(2))) {
            throw new IllegalArgumentException("Billing observation is stale");
        }
        if (!exists || !eligible) throw clearanceRequired();

        UUID sourceId = nullableUuid(data, "sourceId");
        if (!SOURCE_TYPE.equals(text(data, "sourceType")) || !testId.equals(sourceId)) {
            throw clearanceRequired();
        }

        UUID admissionId = nullableUuid(data, "admissionId");
        UUID surgeryCaseId = nullableUuid(data, "surgeryCaseId");
        Instant expiresAt = nullableInstant(data, "expiresAt");
        if (!grant.invoiceId().equals(uuid(data, "invoiceId"))
                || !grant.accountId().equals(uuid(data, "accountId"))
                || !grant.patientId().equals(uuid(data, "patientId"))
                || !PURPOSE.equals(text(data, "purpose"))
                || !EPISODE_TYPE.equals(text(data, "careEpisodeType"))
                || !grant.careEpisodeId().equals(uuid(data, "careEpisodeId"))
                || admissionId != null || surgeryCaseId != null
                || !grant.grantedAt().equals(instant(data, "grantedAt"))
                || !Objects.equals(grant.expiresAt(), expiresAt)
                || grant.isExpiredAt(now)) {
            throw clearanceRequired();
        }
    }

    private void verifyStartReceipt(LabFinancialClearance clearance, UUID testId, String correlation,
            ResponseEntity<String> response) throws Exception {
        JsonNode root = responseRoot(response, correlation);
        JsonNode data = root.get("data");
        if (data == null || !data.isObject()
                || !testId.equals(uuid(data, "operationId"))
                || !clearance.clearanceId().equals(uuid(data, "clearanceId"))
                || !SOURCE_TYPE.equals(text(data, "sourceType"))
                || !testId.equals(uuid(data, "sourceId"))
                || !"STARTED".equals(text(data, "state"))) {
            throw new IllegalArgumentException("Billing admitted a different execution target");
        }
        Instant startedAt = instant(data, "startedAt");
        Instant now = clock.instant();
        // Billing revalidates authority when replaying the stable receipt, whose original timestamp
        // may be older than a network retry. Reject only timestamps that could not have been issued yet.
        if (startedAt.isAfter(now.plusSeconds(5))) {
            throw new IllegalArgumentException("Billing execution receipt is in the future");
        }
    }

    private JsonNode responseRoot(ResponseEntity<String> response, String correlation) throws Exception {
        if (response == null || !response.getStatusCode().is2xxSuccessful() || response.getBody() == null
                || response.getBody().getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES
                || !correlation.equals(response.getHeaders().getFirst(JwtClaims.HEADER_CORRELATION_ID))) {
            throw new IllegalArgumentException("Invalid Billing HTTP response");
        }
        JsonNode root = mapper.readTree(response.getBody());
        if (root == null || !root.isObject() || !bool(root, "success") || !root.has("error")
                || !root.get("error").isNull() || !correlation.equals(text(root, "correlationId"))) {
            throw new IllegalArgumentException("Invalid Billing response envelope");
        }
        return root;
    }

    private String serviceToken() {
        Instant issued = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        return "Bearer " + Jwts.builder().subject("lab-service")
                .claim(JwtClaims.ROLE, "SYSTEM")
                .claim(JwtClaims.TYPE, JwtClaims.SERVICE_TOKEN_TYPE)
                .issuedAt(Date.from(issued)).expiration(Date.from(issued.plusSeconds(60)))
                .signWith(signingKey).compact();
    }

    private static void requireExactLocalTarget(LabFinancialClearance grant, UUID testId) {
        if (grant == null || testId == null || !testId.equals(grant.testId())
                || grant.clearanceTargetId() == null
                || grant.purpose() != ClearancePurpose.LAB_TEST
                || grant.careEpisodeType() != CareEpisodeType.OUTPATIENT_VISIT
                || grant.careEpisodeId() == null || grant.patientId() == null
                || grant.accountId() == null || grant.invoiceId() == null
                || grant.grantedAt() == null) {
            throw clearanceRequired();
        }
    }

    private static void requireCorrelation(String correlation) {
        if (correlation == null || correlation.isBlank() || correlation.length() > 120
                || correlation.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Bounded correlation id required");
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || node.textValue().isBlank()) {
            throw new IllegalArgumentException("Missing Billing response text: " + field);
        }
        return node.textValue();
    }

    private static boolean bool(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isBoolean()) throw new IllegalArgumentException("Missing Billing boolean");
        return node.booleanValue();
    }

    private static UUID uuid(JsonNode root, String field) {
        String raw = text(root, field);
        UUID value = UUID.fromString(raw);
        if (!value.toString().equals(raw)) throw new IllegalArgumentException("Billing UUID is not canonical");
        return value;
    }

    private static UUID nullableUuid(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null) throw new IllegalArgumentException("Missing Billing response field: " + field);
        return node.isNull() ? null : uuid(root, field);
    }

    private static Instant instant(JsonNode root, String field) {
        String raw = text(root, field);
        Instant value = Instant.parse(raw);
        if (!value.toString().equals(raw)) throw new IllegalArgumentException("Billing timestamp is not canonical");
        return value;
    }

    private static Instant nullableInstant(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null) throw new IllegalArgumentException("Missing Billing response field: " + field);
        return node.isNull() ? null : instant(root, field);
    }

    private static LabRuleException clearanceRequired() {
        return new LabRuleException("LAB_CLEARANCE_REQUIRED",
                "Billing no longer authorizes this exact Lab test");
    }
}
