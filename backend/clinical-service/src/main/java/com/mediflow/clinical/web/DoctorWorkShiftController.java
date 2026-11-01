package com.mediflow.clinical.web;

import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.mediflow.clinical.application.service.DoctorWorkShiftApplicationService;
import com.mediflow.clinical.application.dto.request.*;
import com.mediflow.clinical.application.dto.response.*;
import com.mediflow.clinical.application.port.out.*;
import com.mediflow.common.api.*;
import com.mediflow.common.exception.BusinessRuleException;

@RestController
@RequestMapping("/api/v2/doctor-work-shifts")
@ConditionalOnProperty(prefix="mediflow.clinical.scheduling",name="enabled",havingValue="true")
public class DoctorWorkShiftController {
    private final DoctorWorkShiftApplicationService shifts;
    private final CorrelationIdProvider correlations;
    public DoctorWorkShiftController(DoctorWorkShiftApplicationService shifts,CorrelationIdProvider correlations) {
        this.shifts=shifts;this.correlations=correlations;
    }
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DOCTOR')")
    public ResponseEntity<ApiResponse<PageResult<DoctorWorkShiftDTO>>> search(@RequestParam UUID departmentId,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size,HttpServletRequest request) {
        parameters(request,Set.of("departmentId","fromDate","toDate","page","size"));
        return ResponseEntity.ok(ApiResponse.ok(shifts.search(departmentId,fromDate,toDate,PageQuery.of(page,size)),correlations.currentOrCreate().toString()));
    }
    @GetMapping("/mine")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiResponse<List<DoctorWorkShiftDTO>>> mine(
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toDate,HttpServletRequest request) {
        parameters(request,Set.of("fromDate","toDate"));
        return ResponseEntity.ok(ApiResponse.ok(shifts.mine(fromDate,toDate),correlations.currentOrCreate().toString()));
    }
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DOCTOR')")
    public ResponseEntity<ApiResponse<DoctorWorkShiftDTO>> create(@Valid @RequestBody DoctorWorkShiftCreateRequest body,
            @RequestHeader("Idempotency-Key") String key,@RequestHeader("X-Correlation-Id") String correlation) {
        correlation(correlation);return result(shifts.create(body,key));
    }
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DOCTOR')")
    public ResponseEntity<ApiResponse<DoctorWorkShiftDTO>> update(@PathVariable UUID id,@Valid @RequestBody DoctorWorkShiftUpdateRequest body,
            @RequestHeader("Idempotency-Key") String key,@RequestHeader("X-Correlation-Id") String correlation) {
        correlation(correlation);return result(shifts.update(id,body,key));
    }
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DOCTOR')")
    public ResponseEntity<ApiResponse<DoctorWorkShiftDTO>> cancel(@PathVariable UUID id,@Valid @RequestBody DoctorWorkShiftCancelRequest body,
            @RequestHeader("Idempotency-Key") String key,@RequestHeader("X-Correlation-Id") String correlation) {
        correlation(correlation);return result(shifts.cancel(id,body,key));
    }
    @PostMapping("/{id}/consultation-sessions")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DOCTOR')")
    public ResponseEntity<ApiResponse<ConsultationSessionDTO>> session(@PathVariable UUID id,@Valid @RequestBody ConsultationSessionCreateRequest body,
            @RequestHeader("Idempotency-Key") String key,@RequestHeader("X-Correlation-Id") String correlation) {
        correlation(correlation);return result(shifts.createSession(id,body,key));
    }
    private <T> ResponseEntity<ApiResponse<T>> result(IdempotentCommandPort.Result<T> result) {
        var response=ResponseEntity.status(result.status()).header("Idempotency-Replayed",Boolean.toString(result.replayed()));
        if(result.location()!=null) response.location(URI.create(result.location()));
        return response.body(ApiResponse.ok(result.data(),correlations.currentOrCreate().toString()));
    }
    private static void correlation(String value) {
        if(value==null||value.isBlank()||value.length()>120||value.chars().anyMatch(c->c<33||c>126))
            throw new BusinessRuleException("INVALID_REQUEST","X-Correlation-Id must contain 1-120 printable characters");
    }
    private static void parameters(HttpServletRequest request,Set<String> allowed) {
        if(!allowed.containsAll(request.getParameterMap().keySet()))
            throw new BusinessRuleException("INVALID_REQUEST","Unsupported query parameter");
    }
}
