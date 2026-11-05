package com.mediflow.organization.infrastructure.coordination;

import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.patient.application.port.out.PatientAccountOwnerLockPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Co-host bridge for profile email updates and patient account issuance. The transaction-scoped
 * advisory lock belongs to Organization; Patient operations invoked by the callback still open
 * and commit through Patient's independent transaction manager before this lock is released.
 */
@Component
public class PatientAccountOwnerLockAdapter implements PatientAccountOwnerLockPort {
    private final AccountRepository accounts;

    public PatientAccountOwnerLockAdapter(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    @Transactional("organizationTransactionManager")
    public <T> T withOwnerLock(UUID patientId, Supplier<T> action) {
        Objects.requireNonNull(patientId, "patientId");
        Objects.requireNonNull(action, "action");
        accounts.lockOwner(null, patientId);
        return action.get();
    }
}
