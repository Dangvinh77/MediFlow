package com.mediflow.clinical.web;

import com.mediflow.clinical.application.dto.request.ChangeStatusRequest;
import com.mediflow.clinical.application.dto.request.CreateAppointmentRequest;
import com.mediflow.clinical.application.dto.request.UpdateAppointmentRequest;
import com.mediflow.clinical.application.dto.response.AppointmentDTO;
import com.mediflow.clinical.application.port.in.ManageAppointmentUseCase;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** HTTP adapter for appointment queries and lifecycle commands. */
@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private static final String BASE_PATH = "/api/v1/appointments";

    private final ManageAppointmentUseCase manageAppointmentUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DOCTOR', 'NURSE')")
    public ResponseEntity<ApiResponse<PageResult<AppointmentDTO>>> search(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate appointmentDate,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        PageResult<AppointmentDTO> result = manageAppointmentUseCase.search(
                departmentId,
                appointmentDate,
                PageQuery.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<ApiResponse<AppointmentDTO>> getById(
            @PathVariable UUID id) {

        return ResponseEntity.ok(ApiResponse.ok(manageAppointmentUseCase.getById(id)));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<ApiResponse<List<AppointmentDTO>>> getByPatient(
            @PathVariable UUID patientId) {

        return ResponseEntity.ok(ApiResponse.ok(
                manageAppointmentUseCase.byPatient(patientId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'NURSE')")
    public ResponseEntity<ApiResponse<AppointmentDTO>> create(
            @Valid @RequestBody CreateAppointmentRequest request) {

        AppointmentDTO created = manageAppointmentUseCase.create(request);
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + created.appointmentId()))
                .body(ApiResponse.ok(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<ApiResponse<AppointmentDTO>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAppointmentRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(
                manageAppointmentUseCase.update(id, request)));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<ApiResponse<AppointmentDTO>> changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeStatusRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(
                manageAppointmentUseCase.changeStatus(id, request.status())));
    }
}
