package com.mediflow.clinical.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.clinical.application.dto.response.RecordDocumentDTO;
import com.mediflow.clinical.application.dto.response.TimelineEntryDTO;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.service.PatientTimelineApplicationService;
import com.mediflow.clinical.domain.model.MedicalRecordStatus;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.PageResult;

@RestController
public class PatientTimelineController {
    private final PatientTimelineApplicationService service;
    private final CorrelationIdProvider correlationIds;

    public PatientTimelineController(PatientTimelineApplicationService service, CorrelationIdProvider correlationIds) {
        this.service = service;
        this.correlationIds = correlationIds;
    }

    @GetMapping("/api/v2/patients/{patientId}/timeline")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<ApiResponse<PageResult<TimelineEntryDTO>>> timeline(@PathVariable UUID patientId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) MedicalRecordStatus status) {
        return ResponseEntity.ok(ApiResponse.ok(service.timeline(patientId, page, size, departmentId, status),
                correlationIds.currentOrCreate().toString()));
    }

    @GetMapping("/api/v2/records/{recordId}/documents")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<ApiResponse<List<RecordDocumentDTO>>> documents(@PathVariable UUID recordId) {
        return ResponseEntity.ok(ApiResponse.ok(service.documents(recordId), correlationIds.currentOrCreate().toString()));
    }
}
