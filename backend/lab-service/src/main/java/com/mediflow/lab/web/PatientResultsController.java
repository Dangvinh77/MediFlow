package com.mediflow.lab.web;
import com.mediflow.lab.application.service.PatientResultsService;
import com.mediflow.lab.application.dto.response.PatientLabResultDTO;
import com.mediflow.lab.application.port.out.CorrelationIdProvider;
import com.mediflow.common.api.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/lab/mine")
public class PatientResultsController {
    private final PatientResultsService results;
    private final CorrelationIdProvider correlation;
    public PatientResultsController(PatientResultsService results, CorrelationIdProvider correlation) {
        this.results = results; this.correlation = correlation;
    }
    @GetMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ApiResponse<PageResult<PatientLabResultDTO>>> mine(
            @RequestParam(required=false) Integer page, @RequestParam(required=false) Integer size,
            HttpServletRequest request) {
        if (!java.util.Set.of("page", "size").containsAll(request.getParameterMap().keySet())) {
            return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(ApiResponse.fail(
                    new ApiResponse.ApiError("INVALID_REQUEST", "Only page and size are supported", java.util.List.of()),
                    correlation.currentOrCreate().toString()));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(
                results.mine(PageQuery.of(page, size)), correlation.currentOrCreate().toString()));
    }
}
