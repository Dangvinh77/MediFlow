package com.mediflow.billing.web;

import com.mediflow.billing.application.dto.command.FinalizeAdmissionCommand;
import com.mediflow.billing.application.event.LedgerIntegrationEvent.FinalSettlementRecognitionPayload;
import com.mediflow.billing.application.port.in.FinalizeAdmissionUseCase;
import com.mediflow.common.api.ApiResponse;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing/accounts")
@ConditionalOnProperty(name={"mediflow.billing.ledger.enabled","mediflow.billing.settlement-preview.enabled",
        "mediflow.billing.admission-charge-close.enabled","mediflow.billing.final-settlement.enabled"},havingValue="true")
public class FinalAdmissionSettlementController {
    private final FinalizeAdmissionUseCase settlements;
    public FinalAdmissionSettlementController(FinalizeAdmissionUseCase settlements){this.settlements=settlements;}
    @PostMapping("/{id}/final-settlements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ApiResponse<FinalSettlementRecognitionPayload> finalizeAdmission(@PathVariable UUID id,@RequestHeader("Idempotency-Key")String key,
            Authentication actor) {
        return ApiResponse.ok(settlements.finalizeAdmission(new FinalizeAdmissionCommand(id,UUID.fromString(actor.getName()),key)));
    }
}
