package com.mediflow.pharmacy.infrastructure.client;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.pharmacy.application.dto.command.PrescriptionRevisionBillingPayload;
import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import com.mediflow.pharmacy.application.port.out.PrescriptionRevisionBillingPort;
import com.mediflow.pharmacy.infrastructure.security.JwtProperties;
import feign.FeignException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

/** Signs SYSTEM service tokens and validates Billing's response envelope before advancing receipts. */
public final class PharmacyBillingPrescriptionRevisionAdapter implements PrescriptionRevisionBillingPort {
    private static final Logger log = LoggerFactory.getLogger(PharmacyBillingPrescriptionRevisionAdapter.class);
    private final PharmacyBillingRevisionFeignClient client;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final SecretKey key;

    public PharmacyBillingPrescriptionRevisionAdapter(PharmacyBillingRevisionFeignClient client,
            ObjectMapper mapper, Clock clock, JwtProperties jwt) {
        this.client = client;
        this.clock = clock;
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.key = Keys.hmacShaKeyFor(jwt.secret().getBytes(StandardCharsets.UTF_8));
    }

    @Override public void prepare(UUID operationId, PrescriptionRevisionBillingPayload payload, String correlationId) {
        invoke(operationId, payload, correlationId, Operation.PREPARE);
    }
    @Override public void commit(UUID operationId, PrescriptionRevisionBillingPayload payload, String correlationId) {
        invoke(operationId, payload, correlationId, Operation.COMMIT);
    }
    @Override public void abort(UUID operationId, PrescriptionRevisionBillingPayload payload, String correlationId) {
        invoke(operationId, payload, correlationId, Operation.ABORT);
    }

    private void invoke(UUID operationId, PrescriptionRevisionBillingPayload payload, String correlationId, Operation operation) {
        if (operationId == null || payload == null || correlationId == null || correlationId.isBlank()
                || correlationId.length() > 120 || correlationId.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Bounded Billing revision operation identity is required");
        }
        try {
            Instant issued = clock.instant().truncatedTo(ChronoUnit.SECONDS);
            String token = Jwts.builder().subject("pharmacy-service").claim("type", "service").claim("role", "SYSTEM")
                    .issuedAt(Date.from(issued)).expiration(Date.from(issued.plusSeconds(60))).signWith(key).compact();
            ResponseEntity<String> response = switch (operation) {
                case PREPARE -> client.prepare(payload.prescriptionId(), operationId, payload, "Bearer " + token, correlationId);
                case COMMIT -> client.commit(payload.prescriptionId(), operationId, payload, "Bearer " + token, correlationId);
                case ABORT -> client.abort(payload.prescriptionId(), operationId, payload, "Bearer " + token, correlationId);
            };
            validateResponse(response, operationId, payload, correlationId, operation);
        } catch (BusinessRuleException rejected) {
            throw rejected;
        } catch (PharmacyUpstreamUnavailableException unavailable) {
            throw unavailable;
        } catch (Exception failure) {
            // The Feign circuit breaker wraps Billing's HTTP answer (e.g. NoFallbackAvailableException), so a
            // definitive 4xx refusal must be found in the cause chain, not only as a bare FeignException.
            FeignException answer = feignAnswer(failure);
            if (answer == null) {
                log.warn("Billing revision {} for operation {} returned an unusable receipt: {}", operation, operationId,
                        failure.toString());
                throw new PharmacyUpstreamUnavailableException();
            }
            int status = answer.status();
            if (status >= 400 && status < 500 && status != 401 && status != 403 && status != 408 && status != 429) {
                throw businessError(answer.contentUTF8());
            }
            log.warn("Billing revision {} for operation {} answered HTTP {}", operation, operationId, status);
            throw new PharmacyUpstreamUnavailableException();
        }
    }

    private static FeignException feignAnswer(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof FeignException feign) return feign;
        }
        return null;
    }

    private void validateResponse(ResponseEntity<String> response, UUID operationId,
            PrescriptionRevisionBillingPayload payload, String correlationId, Operation operation) throws Exception {
        if (response == null || !response.getStatusCode().is2xxSuccessful() || response.getBody() == null
                || response.getBody().getBytes(StandardCharsets.UTF_8).length > 65536
                || !correlationId.equals(response.getHeaders().getFirst("X-Correlation-Id"))) {
            throw new IllegalArgumentException("Invalid Billing revision response");
        }
        JsonNode root = mapper.readTree(response.getBody());
        if (root == null || !root.isObject() || !root.path("success").isBoolean()
                || !root.path("success").booleanValue() || !root.has("error") || !root.get("error").isNull()
                || !correlationId.equals(requiredText(root, "correlationId"))) {
            throw new IllegalArgumentException("Invalid Billing revision envelope");
        }
        JsonNode data = root.path("data");
        JsonNode revision = data.get("revision");
        if (!data.isObject() || !payload.prescriptionId().equals(requiredUuid(data, "prescriptionId"))
                || !operationId.equals(requiredUuid(data, "operationId"))
                || revision == null || !revision.isIntegralNumber() || revision.longValue() != payload.revision()
                || requiredText(data, "status").isBlank()) {
            throw new IllegalArgumentException("Billing revision receipt does not match the operation");
        }
        String status = requiredText(data, "status");
        boolean valid = switch (operation) {
            case PREPARE -> "PREPARED".equals(status) || "FINALIZED".equals(status);
            case COMMIT -> "FINALIZED".equals(status);
            case ABORT -> "ABORTED".equals(status);
        };
        if (!valid) throw new IllegalArgumentException("Billing returned an incompatible revision state");
    }

    private BusinessRuleException businessError(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode error = root == null ? null : root.get("error");
            if (error != null && error.isObject()) {
                String code = requiredText(error, "code");
                String message = error.path("message").isTextual() ? error.path("message").textValue()
                        : "Billing rejected prescription revision";
                return new BusinessRuleException(code, message);
            }
        } catch (Exception ignored) {
            // An unreadable 4xx is not treated as a transient result because Billing did not accept the command.
        }
        return new BusinessRuleException("BILLING_PRESCRIPTION_REVISION_REJECTED",
                "Billing rejected prescription revision");
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) throw new IllegalArgumentException();
        return value.textValue();
    }

    private static UUID requiredUuid(JsonNode node, String field) {
        String value = requiredText(node, field);
        UUID id = UUID.fromString(value);
        if (!id.toString().equals(value)) throw new IllegalArgumentException();
        return id;
    }

    private enum Operation { PREPARE, COMMIT, ABORT }
}
