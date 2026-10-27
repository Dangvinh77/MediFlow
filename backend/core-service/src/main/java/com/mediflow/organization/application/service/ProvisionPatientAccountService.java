package com.mediflow.organization.application.service;

import com.mediflow.organization.application.port.in.CreateAccountUseCase;
import com.mediflow.organization.application.port.out.AccountCreationReceiptRepository;
import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.application.port.out.PatientAccountIdentityPort;
import com.mediflow.organization.domain.model.Role;
import com.mediflow.common.exception.DuplicateResourceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional("organizationTransactionManager")
public class ProvisionPatientAccountService {
    private final AccountRepository accounts;
    private final AccountCreationReceiptRepository receipts;
    private final CreateAccountUseCase creator;
    private final PatientAccountIdentityPort patients;

    public ProvisionPatientAccountService(AccountRepository accounts, AccountCreationReceiptRepository receipts,
            CreateAccountUseCase creator, PatientAccountIdentityPort patients) {
        this.accounts = accounts; this.receipts = receipts; this.creator = creator; this.patients = patients;
    }

    public CreateAccountUseCase.CreationResult provision(UUID actor, String key, UUID patientId) {
        if (actor == null || patientId == null || key == null || key.isBlank() || key.length() > 160
                || key.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) {
            throw new IllegalArgumentException("Actor, patient and valid Idempotency-Key are required");
        }
        // Match the creator's lock order. Different keys for the same owner serialize below.
        receipts.lockKey(actor, key);
        var previous = receipts.find(actor, key);
        String username;
        if (previous.isPresent()) {
            // Use the immutable issued username. Live Patient identity may since have been
            // corrected or archived. The creator still validates the original receipt's
            // fingerprint, PATIENT/owner binding and initial credential hash below.
            username = accounts.findById(previous.get().accountId())
                    .orElseThrow(() -> new IllegalStateException("Account creation receipt target is missing"))
                    .getUsername();
        } else {
            username = patients.identityNumber(patientId);
            accounts.lockOwner(null, patientId);
            if (accounts.existsByPatientId(patientId)) {
                throw new DuplicateResourceException("PATIENT_ACCOUNT_ALREADY_ISSUED",
                        "Bệnh nhân đã được cấp tài khoản. Dùng tài khoản cũ hoặc phục hồi sau xác minh danh tính.");
            }
        }
        // Preserve the released initial-credential contract; no arbitrary role/password/replacement input.
        return creator.executeWithIdempotency(actor, key, username, "1", null, patientId, Role.PATIENT);
    }
}
