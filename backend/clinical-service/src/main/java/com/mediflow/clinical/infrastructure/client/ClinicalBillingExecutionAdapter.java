package com.mediflow.clinical.infrastructure.client;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.dto.command.FinancialExecutionStartRequest;
import com.mediflow.clinical.application.exception.UpstreamUnavailableException;
import com.mediflow.clinical.application.port.out.FinancialExecutionAuthorityPort;
import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import com.mediflow.common.security.JwtClaims;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

/** Verifies Billing's bounded response before accepting its durable execution admission. */
public final class ClinicalBillingExecutionAdapter implements FinancialExecutionAuthorityPort {
    private static final int MAX_RESPONSE_BYTES = 16_384;
    private static final String SOURCE_TYPE = "EXAM";
    private static final String CARE_EPISODE_TYPE = "OUTPATIENT_VISIT";

    private final ClinicalBillingExecutionFeignClient client;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final CircuitBreaker circuitBreaker;

    public ClinicalBillingExecutionAdapter(ClinicalBillingExecutionFeignClient client, ObjectMapper mapper,
            Clock clock, CircuitBreaker circuitBreaker) {
        this.client = client;
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.clock = clock;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public void start(UUID clearanceId, FinancialExecutionStartRequest request, String correlationId) {
        validateRequest(clearanceId, request, correlationId);
        ResponseEntity<String> response;
        try {
            response = circuitBreaker.executeSupplier(() -> client.start(clearanceId, request, correlationId));
        } catch (FeignException denied) {
            if (denied.status() == 404 || denied.status() == 409) {
                throw new InvalidClinicalDataException("CLINICAL_CLEARANCE_REQUIRED",
                        "Billing no longer authorizes this exact examination target");
            }
            throw unavailable(denied);
        } catch (RuntimeException failure) {
            throw unavailable(failure);
        }
        try {
            verifyResponse(clearanceId, request, correlationId, response);
        } catch (RuntimeException malformed) {
            if (malformed instanceof InvalidClinicalDataException) throw malformed;
            throw unavailable(malformed);
        } catch (Exception malformed) {
            throw unavailable(malformed);
        }
    }

    private void verifyResponse(UUID clearanceId, FinancialExecutionStartRequest request, String correlationId,
            ResponseEntity<String> response) throws Exception {
        if (response == null || !response.getStatusCode().is2xxSuccessful() || response.getBody() == null
                || response.getBody().getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES
                || !correlationId.equals(response.getHeaders().getFirst(JwtClaims.HEADER_CORRELATION_ID))) {
            throw new IllegalArgumentException("Invalid Billing execution-start response");
        }
        JsonNode root = mapper.readTree(response.getBody());
        if (root == null || !root.isObject() || !bool(root, "success") || !root.has("error")
                || !root.get("error").isNull() || !correlationId.equals(text(root, "correlationId"))) {
            throw new IllegalArgumentException("Invalid Billing response envelope");
        }
        JsonNode data = root.get("data");
        if (data == null || !data.isObject()
                || !request.operationId().equals(uuid(data, "operationId"))
                || !clearanceId.equals(uuid(data, "clearanceId"))
                || !request.sourceType().equals(text(data, "sourceType"))
                || !request.sourceId().equals(uuid(data, "sourceId"))
                || !"STARTED".equals(text(data, "state"))) {
            throw new IllegalArgumentException("Billing admitted a different execution target");
        }
        Instant startedAt = instant(data, "startedAt");
        Instant now = clock.instant();
        // A valid idempotent retry replays the original durable receipt after rechecking Billing's
        // current ledger authority. Its timestamp may be old; only a future timestamp is malformed.
        if (startedAt.isAfter(now.plusSeconds(5))) {
            throw new IllegalArgumentException("Billing execution receipt timestamp is in the future");
        }
    }

    private static void validateRequest(UUID clearanceId, FinancialExecutionStartRequest request,
            String correlationId) {
        if (clearanceId == null || request == null || request.operationId() == null
                || !request.operationId().equals(request.sourceId())
                || request.patientId() == null || !SOURCE_TYPE.equals(request.sourceType())
                || !CARE_EPISODE_TYPE.equals(request.careEpisodeType())
                || !request.sourceId().equals(request.careEpisodeId())
                || correlationId == null || correlationId.isBlank() || correlationId.length() > 120
                || correlationId.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("An exact outpatient examination target and bounded correlation are required");
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || node.textValue().isBlank()) {
            throw new IllegalArgumentException("Billing response field is missing: " + field);
        }
        return node.textValue();
    }

    private static boolean bool(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isBoolean()) throw new IllegalArgumentException("Billing boolean is missing");
        return node.booleanValue();
    }

    private static UUID uuid(JsonNode root, String field) {
        String value = text(root, field);
        UUID id = UUID.fromString(value);
        if (!id.toString().equals(value)) throw new IllegalArgumentException("Billing UUID must be canonical");
        return id;
    }

    private static Instant instant(JsonNode root, String field) {
        String value = text(root, field);
        Instant instant = Instant.parse(value);
        if (!instant.toString().equals(value)) throw new IllegalArgumentException("Billing timestamp must be canonical");
        return instant;
    }

    private static UpstreamUnavailableException unavailable(Throwable cause) {
        return new UpstreamUnavailableException("Billing execution authority could not be verified", cause);
    }
}
