package com.mediflow.clinical.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.clinical.application.dto.request.AddCourseVisitRequest;
import com.mediflow.clinical.application.dto.request.CloseTreatmentCourseRequest;
import com.mediflow.clinical.application.dto.request.OpenTreatmentCourseRequest;
import com.mediflow.clinical.application.dto.response.TreatmentCourseDTO;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.service.TreatmentCourseApplicationService;
import com.mediflow.common.api.ApiResponse;

import jakarta.validation.Valid;

@RestController
@ConditionalOnProperty(prefix = "mediflow.v2", name = "treatment-course", havingValue = "true")
public class TreatmentCourseController {
    private final TreatmentCourseApplicationService courses;
    private final CorrelationIdProvider correlationIds;

    public TreatmentCourseController(TreatmentCourseApplicationService courses, CorrelationIdProvider correlationIds) {
        this.courses = courses;
        this.correlationIds = correlationIds;
    }

    @PostMapping("/api/v2/treatment-courses")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<TreatmentCourseDTO>> open(@Valid @RequestBody OpenTreatmentCourseRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        return respond(courses.open(request, key));
    }

    @GetMapping("/api/v2/patients/{patientId}/treatment-courses")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<ApiResponse<List<TreatmentCourseDTO>>> forPatient(@PathVariable UUID patientId) {
        return ResponseEntity.ok(ApiResponse.ok(courses.forPatient(patientId), correlationIds.currentOrCreate().toString()));
    }

    @PostMapping("/api/v2/treatment-courses/{courseId}/visits")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<TreatmentCourseDTO>> addVisit(@PathVariable UUID courseId,
            @Valid @RequestBody AddCourseVisitRequest request, @RequestHeader("Idempotency-Key") String key) {
        return respond(courses.addVisit(courseId, request, key));
    }

    @PutMapping("/api/v2/treatment-courses/{courseId}/closure")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<TreatmentCourseDTO>> close(@PathVariable UUID courseId,
            @Valid @RequestBody CloseTreatmentCourseRequest request, @RequestHeader("Idempotency-Key") String key) {
        return respond(courses.close(courseId, request, key));
    }

    private ResponseEntity<ApiResponse<TreatmentCourseDTO>> respond(IdempotentCommandPort.Result<TreatmentCourseDTO> result) {
        var builder = ResponseEntity.status(result.status()).header("Idempotency-Replayed", Boolean.toString(result.replayed()));
        if (result.location() != null) {
            builder.location(URI.create(result.location()));
        }
        return builder.body(ApiResponse.ok(result.data(), correlationIds.currentOrCreate().toString()));
    }
}
