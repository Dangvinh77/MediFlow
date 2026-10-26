package com.mediflow.clinical.web;

import java.net.URI;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.clinical.application.dto.request.ConsultationSessionCreateRequest;
import com.mediflow.clinical.application.dto.request.ConsultationSessionStatusRequest;
import com.mediflow.clinical.application.dto.request.ConsultationSessionUpdateRequest;
import com.mediflow.clinical.application.dto.response.ConsultationSessionDTO;
import com.mediflow.clinical.application.dto.response.DoctorCalendar;
import com.mediflow.clinical.application.dto.response.RegistrationSummary;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.service.OutpatientRegistrationApplicationService;
import com.mediflow.clinical.application.service.ConsultationSessionApplicationService;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.security.JwtClaims;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v2/consultation-sessions")
@ConditionalOnProperty(prefix = "mediflow.clinical.scheduling", name = "enabled", havingValue = "true")
public class ConsultationSessionController {
    private static final String BASE_PATH = "/api/v2/consultation-sessions";
    private static final Set<String> SEARCH_PARAMETERS = Set.of("departmentId", "fromDate", "toDate", "page", "size");
    private static final Set<String> CALENDAR_PARAMETERS = Set.of("weekStart", "date");
    private static final Set<String> REGISTRATION_PARAMETERS = Set.of("page", "size");
    private final ConsultationSessionApplicationService sessions;
    private final OutpatientRegistrationApplicationService registrations;
    private final CorrelationIdProvider correlationIds;

    public ConsultationSessionController(ConsultationSessionApplicationService sessions,
            OutpatientRegistrationApplicationService registrations,
            CorrelationIdProvider correlationIds) {
        this.sessions = sessions;
        this.registrations = registrations;
        this.correlationIds = correlationIds;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<ConsultationSessionDTO>> create(
            @Valid @RequestBody ConsultationSessionCreateRequest request,
            @RequestHeader(JwtClaims.HEADER_CORRELATION_ID) String correlationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        requireCorrelation(correlationId);
        IdempotentCommandPort.Result<ConsultationSessionDTO> result = sessions.create(request, idempotencyKey);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(result.status())
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()));
        if (result.location() != null) response.location(URI.create(result.location()));
        return response.body(ApiResponse.ok(result.data(), correlationIds.currentOrCreate().toString()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<ConsultationSessionDTO>> update(
            @PathVariable UUID id, @Valid @RequestBody ConsultationSessionUpdateRequest request,
            @RequestHeader(JwtClaims.HEADER_CORRELATION_ID) String correlationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        requireCorrelation(correlationId);
        return idempotent(sessions.update(id, request, idempotencyKey));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<ConsultationSessionDTO>> status(
            @PathVariable UUID id, @Valid @RequestBody ConsultationSessionStatusRequest request,
            @RequestHeader(JwtClaims.HEADER_CORRELATION_ID) String correlationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        requireCorrelation(correlationId);
        return idempotent(sessions.changeStatus(id, request, idempotencyKey));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','NURSE')")
    public ResponseEntity<ApiResponse<PageResult<ConsultationSessionDTO>>> search(
            @RequestParam UUID departmentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest request) {
        requireOnlyParameters(request, SEARCH_PARAMETERS);
        PageResult<ConsultationSessionDTO> result = sessions.search(departmentId, fromDate, toDate, PageQuery.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(result, correlationIds.currentOrCreate().toString()));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<DoctorCalendar>> mine(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest request) {
        requireOnlyParameters(request, CALENDAR_PARAMETERS);
        return ResponseEntity.ok(ApiResponse.ok(sessions.mine(weekStart, date),
                correlationIds.currentOrCreate().toString()));
    }

    @GetMapping("/{id}/registrations")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','NURSE','DOCTOR')")
    public ResponseEntity<ApiResponse<PageResult<RegistrationSummary>>> registrations(
            @PathVariable UUID id, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, HttpServletRequest request) {
        requireOnlyParameters(request, REGISTRATION_PARAMETERS);
        return ResponseEntity.ok(ApiResponse.ok(registrations.sessionRegistrations(id, PageQuery.of(page, size)),
                correlationIds.currentOrCreate().toString()));
    }

    private ResponseEntity<ApiResponse<ConsultationSessionDTO>> idempotent(
            IdempotentCommandPort.Result<ConsultationSessionDTO> result) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(result.status())
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()));
        if (result.location() != null) response.location(URI.create(result.location()));
        return response.body(ApiResponse.ok(result.data(), correlationIds.currentOrCreate().toString()));
    }

    private static void requireCorrelation(String value) {
        if (value == null || value.isBlank() || value.length() > 120
                || value.chars().anyMatch(character -> character < 33 || character > 126)) {
            throw new BusinessRuleException("INVALID_REQUEST", "X-Correlation-Id must be printable and at most 120 characters");
        }
    }

    private static void requireOnlyParameters(HttpServletRequest request, Set<String> allowed) {
        if (!allowed.containsAll(request.getParameterMap().keySet())) {
            throw new BusinessRuleException("INVALID_REQUEST", "Unsupported query parameter");
        }
    }
}
