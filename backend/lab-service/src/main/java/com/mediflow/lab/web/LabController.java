package com.mediflow.lab.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.lab.application.dto.request.AddResultRequest;
import com.mediflow.lab.application.dto.request.ChangeStatusRequest;
import com.mediflow.lab.application.dto.request.CreateLabRequest;
import com.mediflow.lab.application.dto.response.LabTestDTO;
import com.mediflow.lab.application.port.in.ManageLabTestUseCase;
import com.mediflow.lab.domain.model.LabTestStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** HTTP adapter for lab-test queries and lifecycle commands. */
@RestController
@RequestMapping("/api/v1/lab")
@RequiredArgsConstructor
public class LabController {

    private static final String BASE_PATH = "/api/v1/lab";

    private final ManageLabTestUseCase manageLabTestUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'LAB_TECH')")
    public ResponseEntity<ApiResponse<PageResult<LabTestDTO>>> search(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) LabTestStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        PageResult<LabTestDTO> result = manageLabTestUseCase.search(
                departmentId,
                status,
                PageQuery.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<ApiResponse<LabTestDTO>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(manageLabTestUseCase.getById(id)));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public ResponseEntity<ApiResponse<List<LabTestDTO>>> getByPatient(
            @PathVariable UUID patientId) {

        return ResponseEntity.ok(ApiResponse.ok(manageLabTestUseCase.byPatient(patientId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public ResponseEntity<ApiResponse<LabTestDTO>> create(
            @Valid @RequestBody CreateLabRequest request) {

        LabTestDTO created = manageLabTestUseCase.create(request);
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + created.testId()))
                .body(ApiResponse.ok(created));
    }

    @PutMapping("/{id}/results")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_TECH')")
    public ResponseEntity<ApiResponse<LabTestDTO>> addResults(
            @PathVariable UUID id,
            @Valid @RequestBody AddResultRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(
                manageLabTestUseCase.addResults(id, request)));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_TECH')")
    public ResponseEntity<ApiResponse<LabTestDTO>> changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeStatusRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(
                manageLabTestUseCase.changeStatus(id, request.status())));
    }
}
