package com.mediflow.report.infrastructure.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.port.out.CurrentReportAuthorityPort;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

/** Forwards the verified human credential to Core; never caches positive grants. */
public final class CoreReportAuthorityAdapter implements CurrentReportAuthorityPort {
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final Supplier<URI> core;
    private final CircuitBreaker breaker;
    private final Duration timeout;

    public CoreReportAuthorityAdapter(HttpClient http, ObjectMapper mapper, Supplier<URI> core,
            CircuitBreaker breaker, Duration timeout) {
        this.http = http;
        this.mapper = mapper;
        this.core = core;
        this.breaker = breaker;
        this.timeout = timeout;
    }

    @Override
    public boolean permits(UUID accountId, String role, UUID departmentId, String bearer, String correlationId) {
        try {
            return breaker.executeCallable(() -> lookup(accountId, role, departmentId, bearer, correlationId));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception failure) {
            return false;
        }
    }

    private boolean lookup(UUID actor, String role, UUID department, String bearer, String correlation) throws Exception {
        var request = HttpRequest.newBuilder(core.get().resolve("/api/v1/org/me/capabilities"))
                .timeout(timeout).header("Authorization", bearer).header("Accept", "application/json");
        if (correlation != null && !correlation.isBlank()) request.header("X-Correlation-Id", correlation);
        var response = http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
        // Current actor rejection is a denial, not an outage shared by every other actor.
        if (response.statusCode() >= 400 && response.statusCode() < 500 && response.statusCode() != 429) {
            return false;
        }
        if (response.statusCode() != 200) throw new IllegalStateException("Core authority unavailable");
        JsonNode envelope = mapper.readTree(response.body());
        if (envelope == null || !envelope.path("success").isBoolean() || !envelope.path("success").booleanValue()
                || !envelope.path("error").isNull() || !envelope.path("data").isObject()) return false;
        Instant.parse(envelope.path("timestamp").asText());
        var data = envelope.path("data");
        if (!actor.toString().equals(data.path("accountId").asText()) || !role.equals(data.path("role").asText())) {
            return false;
        }
        UUID.fromString(data.path("staffId").asText());
        Instant observed = Instant.parse(data.path("observedAt").asText());
        // Core computes authority per request; reject replayed or implausibly future evidence.
        Instant now = Instant.now();
        if (observed.isBefore(now.minusSeconds(30)) || observed.isAfter(now.plusSeconds(30))) return false;
        var scopes = data.path("managementDepartments");
        if (!scopes.isArray()) return false;
        boolean permitted = false;
        for (var scope : scopes) {
            UUID id = UUID.fromString(scope.path("departmentId").asText());
            if (!scope.path("revision").isIntegralNumber() || !scope.path("revision").canConvertToLong()
                    || scope.path("revision").asLong() < 0 || !scope.path("departmentName").isTextual()) return false;
            // MANAGER assignments carry no appointment; DOCTOR scope comes from an active head appointment.
            if ("DOCTOR".equals(role)) UUID.fromString(scope.path("appointmentId").asText());
            if (department.equals(id)) permitted = true;
        }
        return permitted;
    }
}
