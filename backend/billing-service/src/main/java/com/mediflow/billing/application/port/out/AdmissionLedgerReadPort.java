package com.mediflow.billing.application.port.out;

import java.util.Optional;
import java.util.UUID;
import com.mediflow.billing.domain.model.AdmissionLedgerSnapshot;

public interface AdmissionLedgerReadPort {
    /** All rows must belong to one database snapshot; truncation is an error, never a partial total. */
    Optional<AdmissionLedgerSnapshot> read(UUID accountId);
}
