package com.mediflow.pharmacy.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;
import com.mediflow.pharmacy.application.dto.response.PrescribingContextDTO;
import com.mediflow.pharmacy.application.exception.OutpatientPrescriptionApiException;
import com.mediflow.pharmacy.application.port.in.GetPrescribingContextUseCase;
import com.mediflow.pharmacy.application.service.PrescriptionReadAccessService;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pharmacy/prescriptions")
public class PrescriptionQueryController {
    private final PrescriptionReadAccessService search;
    private final ObjectProvider<GetPrescribingContextUseCase> contexts;
    public PrescriptionQueryController(PrescriptionReadAccessService search, ObjectProvider<GetPrescribingContextUseCase> contexts) {
        this.search = search; this.contexts = contexts;
    }
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','PHARMACIST')")
    public ResponseEntity<ApiResponse<PageResult<PrescriptionDTO>>> search(
            @RequestParam(required=false) String keyword, @RequestParam(required=false) UUID patientId,
            @RequestParam(required=false) String status,
            @RequestParam(required=false) Integer page, @RequestParam(required=false) Integer size,
            Authentication authentication,
            @RequestHeader(value="X-Correlation-Id", required=false) String correlation) {
        String trace = trace(correlation);
        ActorIdentity actor = authentication.getPrincipal() instanceof ActorIdentity identity ? identity : null;
        return ResponseEntity.ok(ApiResponse.ok(search.search(keyword, patientId, status, PageQuery.of(page,size), actor, trace), trace));
    }
    @GetMapping("/prescribing/context")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<ApiResponse<PrescribingContextDTO>> context(@RequestParam UUID recordId, Authentication authentication,
            @RequestHeader(value="X-Correlation-Id", required=false) String correlation) {
        var service = contexts.getIfAvailable();
        if (service == null) throw new OutpatientPrescriptionApiException("PHARMACY_CARE_FINANCE_V2_UNAVAILABLE", "Chức năng kê đơn ngoại trú chưa được bật");
        if (!(authentication.getPrincipal() instanceof ActorIdentity actor)) throw new AccessDeniedException("Cần danh tính nhân viên đã xác thực");
        String trace = trace(correlation);
        return ResponseEntity.ok(ApiResponse.ok(service.get(recordId, actor, trace), trace));
    }
    private String trace(String value) { return value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim(); }
}
