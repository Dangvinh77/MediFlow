package com.mediflow.pharmacy.infrastructure.client;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import com.mediflow.pharmacy.application.port.out.OutpatientPrescriptionContextPort;
import com.mediflow.pharmacy.infrastructure.security.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class PharmacyClinicalContextAdapter implements OutpatientPrescriptionContextPort {
    private static final Set<String> DISPOSITIONS = Set.of("PRESCRIPTION", "OUTPATIENT_FOLLOW_UP", "ADMISSION", "TRANSFER", "OTHER");
    private final PharmacyClinicalFeignClient client;
    private final Clock clock;
    private final SecretKey key;
    private final ObjectMapper mapper;
    public PharmacyClinicalContextAdapter(PharmacyClinicalFeignClient client, Clock clock, JwtProperties jwt, ObjectMapper mapper) {
        this.client = client; this.clock = clock;
        key = Keys.hmacShaKeyFor(jwt.secret().getBytes(StandardCharsets.UTF_8));
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @Override public Observation findRecord(UUID id, String correlation) {
        if (id == null || correlation == null || correlation.isBlank() || correlation.length() > 120
                || correlation.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Exact record and bounded correlation required");
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Clinical lookup must precede mutation locks");
        try {
            var now = clock.instant();
            var issued = now.truncatedTo(ChronoUnit.SECONDS);
            var token = Jwts.builder().subject("pharmacy-service").claim("type", "service").claim("role", "SYSTEM")
                    .issuedAt(Date.from(issued)).expiration(Date.from(issued.plusSeconds(60))).signWith(key).compact();
            var response = client.lookup(id, "Bearer " + token, correlation);
            if (response == null || !response.getStatusCode().is2xxSuccessful() || response.getBody() == null
                    || response.getBody().getBytes(StandardCharsets.UTF_8).length > 1_048_576
                    || !correlation.equals(response.getHeaders().getFirst("X-Correlation-Id"))) throw new IllegalArgumentException();
            var root = mapper.readTree(response.getBody());
            if (root == null || !root.isObject() || !bool(root, "success") || !root.has("error") || !root.get("error").isNull()
                    || !correlation.equals(text(root, "correlationId"))) throw new IllegalArgumentException();
            var data = root.get("data");
            if (data == null || !data.isObject() || !id.equals(uuid(data, "recordId"))) throw new IllegalArgumentException();
            var observed = Instant.parse(text(data, "observedAt"));
            now = clock.instant();
            if (observed.isBefore(now.minusSeconds(30)) || observed.isAfter(now.plusSeconds(5))) throw new IllegalArgumentException();
            boolean exists = bool(data, "exists");
            if (!exists) {
                for (var field : new String[]{"patientId", "doctorId", "departmentId", "careEpisodeType", "careEpisodeId", "recordStatus", "disposition"})
                    if (!data.has(field) || !data.get(field).isNull()) throw new IllegalArgumentException();
                return new Observation(false, id, null, null, null, null, null, null, null, observed);
            }
            var status = text(data, "recordStatus");
            if (!"OUTPATIENT_VISIT".equals(text(data, "careEpisodeType")) || !Set.of("OPEN", "COMPLETED").contains(status)) throw new IllegalArgumentException();
            if (!data.has("disposition")) throw new IllegalArgumentException();
            var disposition = data.get("disposition").isNull() ? null : text(data, "disposition");
            if ("OPEN".equals(status) && disposition != null || "COMPLETED".equals(status)
                    && (disposition == null || !DISPOSITIONS.contains(disposition))) throw new IllegalArgumentException();
            return new Observation(true, id, uuid(data, "patientId"), uuid(data, "doctorId"), uuid(data, "departmentId"),
                    "OUTPATIENT_VISIT", uuid(data, "careEpisodeId"), status, disposition, observed);
        } catch (Exception unavailable) {
            throw new PharmacyUpstreamUnavailableException(); // No HTTP body, token or patient data in error.
        }
    }
    private static String text(JsonNode root, String field) {
        var node = root.get(field); if (node == null || !node.isTextual() || node.textValue().isBlank()) throw new IllegalArgumentException();
        return node.textValue();
    }
    private static boolean bool(JsonNode root, String field) {
        var node = root.get(field); if (node == null || !node.isBoolean()) throw new IllegalArgumentException(); return node.booleanValue();
    }
    private static UUID uuid(JsonNode root, String field) {
        var value = text(root, field); var id = UUID.fromString(value); if (!id.toString().equals(value)) throw new IllegalArgumentException(); return id;
    }
}
