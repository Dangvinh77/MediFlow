package com.mediflow.organization.infrastructure.mail;

import com.mediflow.organization.application.port.out.AccountEmailChangeStore;
import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.application.port.out.PatientAccountIdentityPort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Retryable bridge that keeps Patient's profile email aligned through its public use case. */
@Component
@ConditionalOnProperty(name = "mediflow.patient-email-sync.dispatch-enabled", havingValue = "true", matchIfMissing = true)
public class PatientAccountEmailSyncDispatcher {
    private final AccountEmailChangeStore syncs;
    private final AccountRepository accounts;
    private final PatientAccountIdentityPort patients;

    public PatientAccountEmailSyncDispatcher(AccountEmailChangeStore syncs, AccountRepository accounts,
            PatientAccountIdentityPort patients) {
        this.syncs = syncs;
        this.accounts = accounts;
        this.patients = patients;
    }

    @Scheduled(fixedDelayString = "${mediflow.patient-email-sync.dispatch-delay-ms:5000}")
    @Transactional("organizationTransactionManager")
    public void dispatch() {
        for (var sync : syncs.claimPatientProfileSyncs(10)) {
            try {
                var account = accounts.findByIdForUpdate(sync.accountId()).orElse(null);
                if (account == null || !sync.patientId().equals(account.getPatientId())
                        || !account.isEmailLoginEnabled()) {
                    syncs.completePatientProfileSync(sync.syncId());
                    continue;
                }
                String currentEmail = account.getUsername();
                // The account is the authority for an account-email correction. A legacy Patient
                // profile may have a null or invalid email, so do not read it through the
                // issuance validator before applying the correction via Patient's public use case.
                // That use case is idempotent when the profile already has this exact value.
                patients.updateEmail(sync.patientId(), currentEmail);
                syncs.completePatientProfileSync(sync.syncId());
            } catch (RuntimeException failed) {
                syncs.retryPatientProfileSync(sync.syncId());
            }
        }
    }
}
