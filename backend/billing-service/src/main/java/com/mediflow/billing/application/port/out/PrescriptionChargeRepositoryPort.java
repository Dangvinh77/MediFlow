package com.mediflow.billing.application.port.out;

import com.mediflow.billing.application.dto.command.PrescriptionChargeCommand;
import com.mediflow.billing.domain.model.Charge;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PrescriptionChargeRepositoryPort {
    void claimDelivery(PrescriptionChargeCommand command);
    Optional<IssuedCharge> lockRecordedSource(PrescriptionChargeCommand command);
    UUID openAndLockExactAccount(PrescriptionChargeCommand command);
    IssuedCharge saveChargeAndRequest(PrescriptionChargeCommand command, UUID accountId, Charge charge, Instant issuedAt);

    /** Admission deliberately has no request/invoice; never replace null with fabricated UUIDs. */
    record IssuedCharge(UUID chargeId, UUID accountId, UUID paymentRequestId, UUID invoiceId) { }
}
