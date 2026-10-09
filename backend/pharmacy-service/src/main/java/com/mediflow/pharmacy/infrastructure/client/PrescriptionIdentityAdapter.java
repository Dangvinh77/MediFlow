package com.mediflow.pharmacy.infrastructure.client;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import com.mediflow.pharmacy.application.port.out.PrescriptionIdentityPort;
import com.mediflow.pharmacy.infrastructure.security.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Reads only producer-owned REST identities before ALL stock/receipt transactions. */
public final class PrescriptionIdentityAdapter implements PrescriptionIdentityPort {
    private static final Set<String> JOBS = Set.of("DOCTOR", "NURSE", "TECHNICIAN", "PHARMACIST", "CASHIER", "MANAGER", "ADMINISTRATIVE");
    private final PharmacyPatientFeignClient patients;
    private final PharmacyOrganizationFeignClient organization;
    private final Clock clock;
    private final SecretKey key;
    private final ObjectMapper json;

    public PrescriptionIdentityAdapter(PharmacyPatientFeignClient patients, PharmacyOrganizationFeignClient organization,
            Clock clock, JwtProperties jwt, ObjectMapper json) {
        this.patients = patients; this.organization = organization; this.clock = clock;
        key = Keys.hmacShaKeyFor(jwt.secret().getBytes(StandardCharsets.UTF_8));
        this.json = json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @Override public Observation lookup(UUID patient, UUID doctor, UUID department, String correlation) {
        if (patient == null || doctor == null || department == null || correlation == null || correlation.isBlank()
                || correlation.length() > 120 || correlation.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Exact identities and bounded correlation are required");
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Identity lookup must precede stock/receipt locks");
        var started = clock.instant();
        try {
            var issued = started.truncatedTo(ChronoUnit.SECONDS);
            String auth = "Bearer " + Jwts.builder().subject("pharmacy-service").claim("type", "service")
                    .claim("role", "SYSTEM").issuedAt(Date.from(issued)).expiration(Date.from(issued.plusSeconds(60)))
                    .signWith(key).compact();
            var patientData = data(patients.exists(patient, auth, correlation), correlation);
            if (!patient.equals(uuid(patientData, "patientId"))) throw new IllegalArgumentException();
            boolean patientExists = bool(patientData, "exists");
            var staff = data(organization.staff(doctor, auth, correlation), correlation);
            boolean staffExists = bool(staff, "exists"), active = bool(staff, "active");
            String job = nullableText(staff, "jobTitle");
            UUID staffDepartment = nullableUuid(staff, "departmentId");
            var roles = staff.get("eligibleTeamRoles");
            if (roles == null || !roles.isArray() || !staffExists && (active || job != null || staffDepartment != null)
                    || !active && !roles.isEmpty()
                    || staffExists && (job == null || !JOBS.contains(job) || staffDepartment == null)) throw new IllegalArgumentException();
            for (var role : roles) if (!role.isTextual()) throw new IllegalArgumentException();
            // Team-role projection is descriptive. Never use it as prescribing authority.
            boolean eligibleDoctor = staffExists && active && "DOCTOR".equals(job);
            var dept = data(organization.department(department, auth, correlation), correlation);
            if (!department.equals(uuid(dept, "departmentId"))) throw new IllegalArgumentException();
            boolean deptExists = bool(dept, "exists"), deptActive = bool(dept, "active");
            String name = nullableText(dept, "departmentName"), type = nullableText(dept, "departmentType");
            if (!deptExists && (deptActive || name != null || type != null)
                    || deptExists && (name == null || type == null)) throw new IllegalArgumentException();
            var observation = new Observation(patient, patientExists, doctor, staffExists, eligibleDoctor,
                    staffDepartment, department, deptExists && deptActive, started);
            // Slow upstream calls cannot manufacture a fresh permission snapshot at completion.
            if (started.isBefore(clock.instant().minusSeconds(30)) || started.isAfter(clock.instant().plusSeconds(5)))
                throw new IllegalArgumentException();
            return observation;
        } catch (Exception unavailable) { throw new PharmacyUpstreamUnavailableException(); }
    }

    private JsonNode data(ResponseEntity<String> response, String correlation) throws Exception {
        if (response == null || !response.getStatusCode().is2xxSuccessful() || response.getBody() == null
                || response.getBody().getBytes(StandardCharsets.UTF_8).length > 1_048_576
                || !correlation.equals(response.getHeaders().getFirst("X-Correlation-Id"))) throw new IllegalArgumentException();
        var envelope = json.readTree(response.getBody());
        if (envelope == null || !envelope.isObject() || !bool(envelope, "success")
                || !envelope.has("error") || !envelope.get("error").isNull()
                || !correlation.equals(text(envelope, "correlationId"))) throw new IllegalArgumentException();
        var data = envelope.get("data");
        if (data == null || !data.isObject()) throw new IllegalArgumentException();
        return data;
    }
    private static boolean bool(JsonNode node, String field) {
        var value = node.get(field); if (value == null || !value.isBoolean()) throw new IllegalArgumentException(); return value.booleanValue();
    }
    private static String text(JsonNode node, String field) {
        var value = node.get(field); if (value == null || !value.isTextual() || value.textValue().isBlank()) throw new IllegalArgumentException(); return value.textValue();
    }
    private static String nullableText(JsonNode node, String field) {
        if (!node.has(field)) throw new IllegalArgumentException(); return node.get(field).isNull() ? null : text(node, field);
    }
    private static UUID uuid(JsonNode node, String field) {
        var text = text(node, field); var id = UUID.fromString(text); if (!id.toString().equals(text)) throw new IllegalArgumentException(); return id;
    }
    private static UUID nullableUuid(JsonNode node, String field) {
        if (!node.has(field)) throw new IllegalArgumentException(); return node.get(field).isNull() ? null : uuid(node, field);
    }
}
