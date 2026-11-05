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
import com.mediflow.organization.application.dto.response.MailDeliveryDTO;
import com.mediflow.organization.application.port.out.SecurityMailStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import com.mediflow.organization.application.service.SecurityMailTemplate;

@Service
@Transactional("organizationTransactionManager")
public class ProvisionPatientAccountService {
    private final AccountRepository accounts;
    private final AccountCreationReceiptRepository receipts;
    private final CreateAccountUseCase creator;
    private final PatientAccountIdentityPort patients;
    private final SecurityMailStore mail;
    private final SecurityMailTemplate templates;
    private final String fromEmail;

    @Autowired
    public ProvisionPatientAccountService(AccountRepository accounts, AccountCreationReceiptRepository receipts,
            CreateAccountUseCase creator, PatientAccountIdentityPort patients, SecurityMailStore mail,
            SecurityMailTemplate templates,
            @Value("${mediflow.mail.from:noreply@mediflow.local}") String fromEmail) {
        this.accounts = accounts; this.receipts = receipts; this.creator = creator; this.patients = patients;
        this.mail = mail; this.templates = templates; this.fromEmail = fromEmail;
    }

    public ProvisionPatientAccountService(AccountRepository accounts, AccountCreationReceiptRepository receipts,
            CreateAccountUseCase creator, PatientAccountIdentityPort patients) {
        this(accounts, receipts, creator, patients, null, null, "noreply@mediflow.local");
    }

    public CreateAccountUseCase.CreationResult provision(UUID actor, String key, UUID patientId) {
        return provisionWithMail(actor, key, patientId).creation();
    }

    public ProvisionedAccount provisionWithMail(UUID actor, String key, UUID patientId) {
        if (actor == null || patientId == null || key == null || key.isBlank() || key.length() > 160
                || key.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) {
            throw new IllegalArgumentException("Actor, patient and valid Idempotency-Key are required");
        }
        // Match the creator's lock order. Different keys for the same owner serialize below.
        receipts.lockKey(actor, key);
        var previous = receipts.find(actor, key);
        String email;
        if (previous.isPresent()) {
            // Use the immutable issued email on replay. A later profile correction must not
            // turn an idempotency replay into a second account or another credential.
            email = accounts.findById(previous.get().accountId())
                    .orElseThrow(() -> new IllegalStateException("Account creation receipt target is missing"))
                    .getUsername();
        } else {
            accounts.lockOwner(null, patientId);
            if (accounts.existsByPatientId(patientId)) {
                throw new DuplicateResourceException("PATIENT_ACCOUNT_ALREADY_ISSUED",
                        "Bệnh nhân đã được cấp tài khoản. Dùng tài khoản cũ hoặc phục hồi sau xác minh danh tính.");
            }
            // Read only after taking the owner lock shared with Patient profile email writes.
            // If a correction wins first, issuance sees the committed email; if issuance wins,
            // the correction waits and then observes the newly linked account.
            email = patients.email(patientId);
        }
        var creation = creator.issueWithIdempotency(actor, key, email, null, patientId, Role.PATIENT, false);
        MailDeliveryDTO delivery = null;
        if (mail != null && templates != null) {
            String operation = "patient-issue:" + sha256(actor + ":" + key);
            var existingMail = mail.byOperationKey(operation);
            if (existingMail.isPresent()) delivery = existingMail.get();
            else {
                if (creation.temporaryPassword() == null) throw new IllegalStateException("Original account email receipt is missing");
                String displayName = patients.displayName(patientId);
                var rendered = templates.credential(displayName, email, creation.temporaryPassword());
                delivery = mail.enqueue(new SecurityMailStore.NewMessage(creation.account().getAccountId(),
                        fromEmail, email, "ACCOUNT_ISSUED", operation, rendered.subject(),
                        rendered.textBody(), rendered.htmlBody()));
            }
        }
        return new ProvisionedAccount(creation, delivery);
    }

    private static String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (Exception failure) { throw new IllegalStateException("SHA-256 is unavailable", failure); }
    }

    public record ProvisionedAccount(CreateAccountUseCase.CreationResult creation, MailDeliveryDTO mailDelivery) { }
}
