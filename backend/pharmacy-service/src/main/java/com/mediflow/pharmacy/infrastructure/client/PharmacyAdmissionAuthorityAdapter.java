package com.mediflow.pharmacy.infrastructure.client;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import com.mediflow.pharmacy.application.port.out.AdmissionAuthorityPort;
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

/** Exact current Inpatient lookup, performed only outside a stock/mutation transaction. */
public final class PharmacyAdmissionAuthorityAdapter implements AdmissionAuthorityPort {
    private static final Set<String> STATUSES = Set.of("REQUESTED", "AWAITING_BED", "AWAITING_DEPOSIT",
            "READY", "ADMITTED", "MEDICALLY_DISCHARGED", "CLOSED", "CANCELLED");
    private final PharmacyInpatientFeignClient client;
    private final Clock clock;
    private final SecretKey key;
    private final ObjectMapper mapper;

    public PharmacyAdmissionAuthorityAdapter(PharmacyInpatientFeignClient client, Clock clock,
            JwtProperties properties, ObjectMapper mapper) {
        this.client = client; this.clock = clock;
        key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @Override
    public Observation findAdmission(UUID admissionId, String correlation) {
        if (admissionId == null || correlation == null || correlation.isBlank() || correlation.length() > 120)
            throw new IllegalArgumentException("Exact admission and bounded correlation are required");
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Admission authority lookup must precede mutation locks");
        try {
            Instant issued = clock.instant().truncatedTo(ChronoUnit.SECONDS);
            String token = Jwts.builder().subject("pharmacy-service").claim("type", "service").claim("role", "SYSTEM")
                    .issuedAt(Date.from(issued)).expiration(Date.from(issued.plusSeconds(60))).signWith(key).compact();
            var response = client.lookup(admissionId, "Bearer " + token, correlation);
            if (response == null || !response.getStatusCode().is2xxSuccessful()
                    || !correlation.equals(response.getHeaders().getFirst("X-Correlation-Id"))
                    || response.getBody() == null || response.getBody().getBytes(StandardCharsets.UTF_8).length > 1_048_576)
                throw new PharmacyUpstreamUnavailableException();
            var root = mapper.readTree(response.getBody());
            if (root == null || !root.isObject() || !bool(root, "success") || !root.has("error")
                    || !root.get("error").isNull() || !correlation.equals(text(root, "correlationId")))
                throw new PharmacyUpstreamUnavailableException();
            var data = root.get("data");
            if (data == null || !data.isObject() || !admissionId.equals(uuid(data, "admissionId")))
                throw new PharmacyUpstreamUnavailableException();
            boolean exists = bool(data, "exists"), eligible = bool(data, "eligible");
            Instant observed = Instant.parse(text(data, "observedAt")), now = clock.instant();
            if (observed.isBefore(now.minusSeconds(30)) || observed.isAfter(now.plusSeconds(5)))
                throw new PharmacyUpstreamUnavailableException();
            if (!exists) {
                if (eligible) throw new PharmacyUpstreamUnavailableException();
                for (String field : new String[]{"patientId", "departmentId", "sourceRecordId", "status", "sourceRevision"})
                    if (!data.has(field) || !data.get(field).isNull()) throw new PharmacyUpstreamUnavailableException();
                return new Observation(admissionId, null, null, null, false, false, null, null, observed);
            }
            String status = text(data, "status"), revision = text(data, "sourceRevision");
            if (!STATUSES.contains(status) || eligible && !"ADMITTED".equals(status)
                    || !revision.matches("0|[1-9][0-9]*") || Long.parseLong(revision) < 0)
                throw new PharmacyUpstreamUnavailableException();
            return new Observation(admissionId, uuid(data, "patientId"), uuid(data, "departmentId"),
                    uuid(data, "sourceRecordId"), true, eligible, status, revision, observed);
        } catch (Exception failure) {
            // No token, source payload or HTTP exception body is retained in the public error.
            throw new PharmacyUpstreamUnavailableException();
        }
    }

    private static String text(JsonNode root, String field) {
        var value = root.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) throw new IllegalArgumentException();
        return value.textValue();
    }
    private static boolean bool(JsonNode root, String field) {
        var value = root.get(field); if (value == null || !value.isBoolean()) throw new IllegalArgumentException();
        return value.booleanValue();
    }
    private static UUID uuid(JsonNode root, String field) {
        String text = text(root, field); UUID value = UUID.fromString(text);
        if (!value.toString().equals(text)) throw new IllegalArgumentException(); return value;
    }
}
