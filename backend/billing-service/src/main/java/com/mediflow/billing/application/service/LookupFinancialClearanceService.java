package com.mediflow.billing.application.service;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import com.mediflow.billing.application.dto.response.FinancialClearanceLookupDTO;
import com.mediflow.billing.application.port.in.LookupFinancialClearanceUseCase;
import com.mediflow.billing.application.port.out.FinancialClearanceAuthorityPort;

public final class LookupFinancialClearanceService implements LookupFinancialClearanceUseCase {
    private final FinancialClearanceAuthorityPort repository;
    private final Clock clock;

    public LookupFinancialClearanceService(FinancialClearanceAuthorityPort repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override public FinancialClearanceLookupDTO lookup(UUID id) {
        Objects.requireNonNull(id, "Clearance identity required");
        return repository.find(id).map(value -> new FinancialClearanceLookupDTO(true, value.clearanceId(), value.eligible(),
                value.invoiceId(), value.accountId(), value.patientId(), value.purpose(), value.careEpisodeType(),
                value.careEpisodeId(), value.admissionId(), value.surgeryCaseId(), value.grantedAt(), value.expiresAt(),
                value.observedAt())).orElseGet(() -> new FinancialClearanceLookupDTO(false, id, false,
                null, null, null, null, null, null, null, null, null, null, clock.instant()));
    }
}
