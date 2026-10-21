package com.mediflow.clinical.infrastructure.client;

import com.mediflow.clinical.application.dto.command.FinancialExecutionStartRequest;
import com.mediflow.common.security.JwtClaims;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** Private Billing command. Authorization is supplied by Clinical's service-token interceptor. */
@FeignClient(contextId = "clinicalBillingExecutionClient", name = "${mediflow.clients.billing-service:billing-service}")
public interface ClinicalBillingExecutionFeignClient {

    @PostMapping("/api/v1/billing/financial-clearances/{clearanceId}/execution-starts")
    ResponseEntity<String> start(@PathVariable UUID clearanceId,
            @RequestBody FinancialExecutionStartRequest request,
            @RequestHeader(JwtClaims.HEADER_CORRELATION_ID) String correlationId);
}
