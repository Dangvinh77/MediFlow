package com.mediflow.lab.infrastructure.client;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.lab.application.dto.context.LabClinicalOrderContext;
import com.mediflow.lab.application.exception.LabUpstreamUnavailableException;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContext;
import com.mediflow.lab.application.port.out.LabClinicalOrderContextPort;
import com.mediflow.lab.domain.model.CareEpisodeType;
import com.mediflow.lab.infrastructure.security.JwtProperties;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Issues a short-lived actor-delegating service token and strictly verifies Clinical's response. */
public final class LabClinicalOrderContextAdapter implements LabClinicalOrderContextPort {
    private static final String ORIGINAL_ACTOR_ROLE_CLAIM = "originalActorRole";
    private static final int MAX_BODY_BYTES = 16_384;
    private static final Set<String> ACTOR_ROLES = Set.of("DOCTOR", "ADMIN");

    private final LabClinicalOrderFeignClient client;
    private final Clock clock;
    private final SecretKey key;
    private final ObjectMapper mapper;
    private final CircuitBreaker circuitBreaker;

    public LabClinicalOrderContextAdapter(LabClinicalOrderFeignClient client, Clock clock, JwtProperties jwt,
            ObjectMapper mapper, CircuitBreaker circuitBreaker) {
        this.client = client;
        this.clock = clock;
        this.key = Keys.hmacShaKeyFor(jwt.secret().getBytes(StandardCharsets.UTF_8));
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public Optional<LabClinicalOrderContext> findById(UUID sourceOrderId, String correlationId,
            AuthenticatedStaffContext actor) {
        if (sourceOrderId == null || correlationId == null || correlationId.isBlank()
                || correlationId.length() > 120
                || correlationId.chars().anyMatch(Character::isISOControl)
                || actor == null || actor.staffId() == null || !ACTOR_ROLES.contains(actor.role())) {
            throw new IllegalArgumentException("An exact source order and bounded correlation are required");
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Clinical authority must be checked before Lab write locks");
        }

        ResponseEntity<String> response;
        try {
            String authorization = "Bearer " + serviceToken(actor);
            response = circuitBreaker.executeSupplier(
                    () -> client.lookup(sourceOrderId, authorization, correlationId));
        } catch (FeignException forbidden) {
            if (forbidden.status() == 403) {
                throw new ForbiddenOperationException("LAB_SOURCE_ORDER_FORBIDDEN",
                        "The authenticated Lab actor is not permitted to use this Clinical order");
            }
            throw new LabUpstreamUnavailableException();
        } catch (RuntimeException unavailable) {
            throw new LabUpstreamUnavailableException();
        }

        try {
            return parseResponse(sourceOrderId, correlationId, response);
        } catch (Exception malformedOrUnavailable) {
            throw new LabUpstreamUnavailableException();
        }
    }

    private String serviceToken(AuthenticatedStaffContext actor) {
        var now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        return Jwts.builder()
                .subject("lab-service")
                .claim(JwtClaims.ROLE, "SYSTEM")
                .claim(JwtClaims.TYPE, JwtClaims.SERVICE_TOKEN_TYPE)
                .claim(JwtClaims.STAFF_ID, actor.staffId().toString())
                .claim(ORIGINAL_ACTOR_ROLE_CLAIM, actor.role())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60)))
                .signWith(key)
                .compact();
    }

    private Optional<LabClinicalOrderContext> parseResponse(UUID requestedId, String correlationId,
            ResponseEntity<String> response) throws Exception {
        if (response == null || !response.getStatusCode().is2xxSuccessful() || response.getBody() == null
                || response.getBody().getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES
                || !correlationId.equals(response.getHeaders().getFirst(JwtClaims.HEADER_CORRELATION_ID))) {
            throw new IllegalArgumentException("Invalid Clinical authority response");
        }
        JsonNode root = mapper.readTree(response.getBody());
        if (root == null || !root.isObject() || !bool(root, "success") || !root.has("error")
                || !root.get("error").isNull() || !correlationId.equals(text(root, "correlationId"))) {
            throw new IllegalArgumentException("Invalid Clinical authority envelope");
        }
        JsonNode data = root.get("data");
        if (data == null || !data.isObject() || !requestedId.equals(uuid(data, "sourceOrderId"))) {
            throw new IllegalArgumentException("Clinical authority returned a different order");
        }
        Instant observedAt = instant(data, "observedAt");
        Instant now = clock.instant();
        if (observedAt.isBefore(now.minusSeconds(30)) || observedAt.isAfter(now.plusSeconds(5))) {
            throw new IllegalArgumentException("Clinical authority observation is stale");
        }
        if (!bool(data, "exists")) {
            for (String field : new String[]{"recordId", "patientId", "requestingDoctorId",
                    "requestingDepartmentId", "careEpisodeType", "careEpisodeId", "labType",
                    "requestedDate", "priceCode", "orderedAt"}) {
                if (!data.has(field) || !data.get(field).isNull()) {
                    throw new IllegalArgumentException("Absent Clinical context contains order details");
                }
            }
            return Optional.empty();
        }

        CareEpisodeType episodeType = CareEpisodeType.valueOf(text(data, "careEpisodeType"));
        String labType = text(data, "labType");
        String priceCode = text(data, "priceCode");
        LocalDate requestedDate = localDate(data, "requestedDate");
        if (episodeType != CareEpisodeType.OUTPATIENT_VISIT || labType.length() > 50
                || priceCode.length() > 64) {
            throw new IllegalArgumentException("Clinical order is outside the outpatient Lab contract");
        }
        Instant orderedAt = instant(data, "orderedAt");
        if (orderedAt.isAfter(observedAt.plusSeconds(5)) || orderedAt.isAfter(now.plusSeconds(5))) {
            throw new IllegalArgumentException("Clinical order timestamp is after its observation");
        }
        return Optional.of(new LabClinicalOrderContext(requestedId, uuid(data, "recordId"),
                uuid(data, "patientId"), uuid(data, "requestingDoctorId"),
                uuid(data, "requestingDepartmentId"), episodeType, uuid(data, "careEpisodeId"),
                labType, requestedDate, priceCode, orderedAt, observedAt));
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || node.textValue().isBlank()) {
            throw new IllegalArgumentException("Required text field is missing");
        }
        return node.textValue();
    }

    private static boolean bool(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isBoolean()) throw new IllegalArgumentException("Required boolean is missing");
        return node.booleanValue();
    }

    private static UUID uuid(JsonNode root, String field) {
        String value = text(root, field);
        UUID id = UUID.fromString(value);
        if (!id.toString().equals(value)) throw new IllegalArgumentException("UUID must use canonical lowercase format");
        return id;
    }

    private static LocalDate localDate(JsonNode root, String field) {
        String value = text(root, field);
        LocalDate date = LocalDate.parse(value);
        if (!date.toString().equals(value)) throw new IllegalArgumentException("Date must use ISO format");
        return date;
    }

    private static Instant instant(JsonNode root, String field) {
        String value = text(root, field);
        Instant instant = Instant.parse(value);
        if (!instant.toString().equals(value)) throw new IllegalArgumentException("Instant must use canonical UTC format");
        return instant;
    }
}
