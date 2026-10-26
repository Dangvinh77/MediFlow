package com.mediflow.billing.web;
import com.mediflow.billing.application.dto.response.ScheduledPaymentQueueItem;
import com.mediflow.billing.application.port.in.FindScheduledPaymentQueueUseCase;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.security.JwtClaims;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/billing/payment-requests")
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.scheduled-payments.enabled"}, havingValue = "true")
public class ScheduledPaymentQueueController {
    private final FindScheduledPaymentQueueUseCase queue;
    public ScheduledPaymentQueueController(FindScheduledPaymentQueueUseCase queue) { this.queue = queue; }
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<PageResult<ScheduledPaymentQueueItem>>> find(
            @RequestParam(required = false) UUID patientId, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestHeader(value = JwtClaims.HEADER_CORRELATION_ID, required = false) String suppliedCorrelation) {
        String correlation = suppliedCorrelation == null || suppliedCorrelation.isBlank() ? UUID.randomUUID().toString() : suppliedCorrelation;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(JwtClaims.HEADER_CORRELATION_ID, correlation)
                .body(ApiResponse.ok(queue.find(patientId, PageQuery.of(page, size)), correlation));
    }
}
