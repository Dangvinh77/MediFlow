package com.mediflow.organization.application.service;

import com.mediflow.organization.application.dto.response.MailDeliveryDTO;
import com.mediflow.organization.application.port.in.CreateAccountUseCase;
import com.mediflow.organization.application.port.out.AccountRecoveryTokenStore;
import com.mediflow.organization.application.port.out.AccountEmailChangeStore;
import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.application.port.out.PatientAccountSecurityAuditStore;
import com.mediflow.organization.application.port.out.PatientAccountIdentityPort;
import com.mediflow.organization.application.port.out.PasswordHasher;
import com.mediflow.organization.application.port.out.SecurityMailStore;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.exception.AccountNotFoundException;
import com.mediflow.organization.domain.exception.InvalidAccountException;
import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.domain.model.Role;
import com.mediflow.organization.domain.model.Staff;
import com.mediflow.organization.domain.model.JobTitle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional("organizationTransactionManager")
public class SecurityAccountOperationsService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%".toCharArray();
    private final CreateAccountUseCase creator;
    private final AccountRepository accounts;
    private final StaffRepository staff;
    private final PatientAccountIdentityPort patients;
    private final PasswordHasher passwords;
    private final SecurityMailStore mail;
    private final AccountRecoveryTokenStore recoveryTokens;
    private final AccountEmailChangeStore emailChanges;
    private final PatientAccountSecurityAuditStore patientSecurityAudit;
    private final SecurityMailTemplate templates;
    private final String fromEmail;

    public SecurityAccountOperationsService(CreateAccountUseCase creator, AccountRepository accounts,
            StaffRepository staff, PatientAccountIdentityPort patients, PasswordHasher passwords,
            SecurityMailStore mail, AccountRecoveryTokenStore recoveryTokens, AccountEmailChangeStore emailChanges,
            PatientAccountSecurityAuditStore patientSecurityAudit,
            SecurityMailTemplate templates,
            @Value("${mediflow.mail.from:noreply@mediflow.local}") String fromEmail) {
        this.creator = creator;
        this.accounts = accounts;
        this.staff = staff;
        this.patients = patients;
        this.passwords = passwords;
        this.mail = mail;
        this.recoveryTokens = recoveryTokens;
        this.emailChanges = emailChanges;
        this.patientSecurityAudit = patientSecurityAudit;
        this.templates = templates;
        this.fromEmail = fromEmail;
    }

    public IssueResult issueStaff(UUID actor, String key, String requestedEmail, UUID staffId,
            Role role, boolean replaceActive) {
        if (staffId == null || role == null || role == Role.PATIENT || role == Role.SYSTEM) {
            throw new InvalidAccountException("A staff account requires a staff owner and human staff role");
        }
        Staff owner = staff.findById(staffId)
                .orElseThrow(() -> new InvalidAccountException("Staff account owner was not found"));
        if (!owner.isActive()) throw new InvalidAccountException("Inactive staff cannot receive an account");
        Role expectedRole = roleFor(owner.getJobTitle());
        if (role != expectedRole) {
            throw new InvalidAccountException("Account role must match the selected staff profile job title");
        }
        String email = normalizeEmail(owner.getEmail());
        if (requestedEmail != null && !normalizeEmail(requestedEmail).equals(email)) {
            throw new InvalidAccountException("Account email must match the selected staff profile");
        }
        var creation = creator.issueWithIdempotency(actor, key, email, staffId, null, role, replaceActive);
        String operation = operationKey("staff-issue", actor, key);
        MailDeliveryDTO delivery = deliveryForIssue(creation, operation, owner.getFullName(), email);
        return new IssueResult(creation.account(), creation.replayed(), delivery);
    }

    public MailDeliveryDTO resetPassword(UUID actor, UUID accountId, String idempotencyKey) {
        String operation = operationKey("admin-reset:" + accountId, actor, idempotencyKey);
        mail.lockOperation(operation);
        var prior = mail.byOperationKey(operation);
        if (prior.isPresent()) return prior.get();
        Account account = accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        return resetPasswordLocked(account, operation);
    }

    public MailDeliveryDTO resetPatientPassword(UUID actor, UUID patientId, String idempotencyKey,
            UUID correlationId) {
        if (patientId == null || correlationId == null) {
            throw new IllegalArgumentException("Patient and correlation ids are required");
        }
        String operation = operationKey("patient-reset:" + patientId, actor, idempotencyKey);
        mail.lockOperation(operation);
        var prior = mail.byOperationKey(operation);
        if (prior.isPresent()) return prior.get();

        // The Patient use case enforces that the profile exists, is not archived, and has a
        // deliverable profile email. Resolve the target by its owner UUID, never by email.
        String profileEmail = patients.email(patientId);
        Account linked = requirePatientAccount(patientId);
        Account account = accounts.findByIdForUpdate(linked.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException(linked.getAccountId()));
        requirePatientOwner(account, patientId);
        requireActivePatientAccount(account);
        profileEmail = patients.email(patientId);
        requireSynchronizedEmail(profileEmail, account);

        MailDeliveryDTO delivery = resetPasswordLocked(account, operation);
        patientSecurityAudit.record(PatientAccountSecurityAuditStore.Action.PATIENT_PASSWORD_RESET,
                actor, patientId, account.getAccountId(), correlationId, operation);
        return delivery;
    }

    private MailDeliveryDTO resetPasswordLocked(Account account, String operation) {
        UUID accountId = account.getAccountId();
        if (account.getRole() == Role.SYSTEM) throw new InvalidAccountException("Service accounts cannot receive password resets");
        String email = normalizeEmail(account.getUsername());
        String temporaryPassword = randomPassword();
        account.changePassword(passwords.hash(temporaryPassword));
        account.requirePasswordChangeUntil(Instant.now().plusSeconds(24 * 60 * 60));
        accounts.save(account);
        accounts.incrementCredentialVersion(accountId);
        recoveryTokens.invalidateUnused(accountId, Instant.now());
        var rendered = templates.credential(displayName(account), email, temporaryPassword);
        return mail.enqueue(new SecurityMailStore.NewMessage(accountId, fromEmail, email, "ADMIN_RESET",
                operation, rendered.subject(), rendered.textBody(), rendered.htmlBody()));
    }

    public MailDeliveryDTO latestDelivery(UUID accountId) {
        if (!accounts.findById(accountId).isPresent()) throw new AccountNotFoundException(accountId);
        return mail.latestForAccount(accountId)
                .orElseThrow(() -> new com.mediflow.common.exception.ResourceNotFoundException(
                        "MAIL_DELIVERY_NOT_FOUND", "No security email has been issued for this account"));
    }

    public MailDeliveryDTO latestPatientDelivery(UUID patientId) {
        // Read by owner id, while also confirming that the Patient profile itself still exists.
        patients.profileEmail(patientId);
        Account account = requirePatientAccount(patientId);
        return latestDelivery(account.getAccountId());
    }

    public MailDeliveryDTO retryDelivery(UUID accountId, UUID messageId) {
        return mail.retry(accountId, messageId);
    }

    public MailDeliveryDTO retryPatientDelivery(UUID actor, UUID patientId, UUID messageId,
            UUID correlationId) {
        if (actor == null || patientId == null || messageId == null || correlationId == null) {
            throw new IllegalArgumentException("Actor, patient, message, and correlation ids are required");
        }
        Account linked = requirePatientAccount(patientId);
        Account account = accounts.findByIdForUpdate(linked.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException(linked.getAccountId()));
        requirePatientOwner(account, patientId);
        requireActivePatientAccount(account);
        String profileEmail = patients.email(patientId);
        requireSynchronizedEmail(profileEmail, account);
        MailDeliveryDTO delivery = mail.retry(account.getAccountId(), messageId, account.getUsername());
        patientSecurityAudit.record(PatientAccountSecurityAuditStore.Action.PATIENT_MAIL_RETRY,
                actor, patientId, account.getAccountId(), correlationId, null);
        return delivery;
    }

    private Account requirePatientAccount(UUID patientId) {
        List<Account> linked = accounts.findByOwner(null, patientId);
        Account account = linked.stream()
                .sorted(Comparator.comparing(Account::isActive).reversed()
                        .thenComparing(Account::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Account::getAccountId, Comparator.reverseOrder()))
                .findFirst()
                .orElseThrow(() -> new com.mediflow.common.exception.ResourceNotFoundException(
                        "PATIENT_ACCOUNT_NOT_FOUND", "No account is linked to this patient profile"));
        requirePatientOwner(account, patientId);
        return account;
    }

    private static void requirePatientOwner(Account account, UUID patientId) {
        if (account.getRole() != Role.PATIENT || !patientId.equals(account.getPatientId())
                || account.getStaffId() != null) {
            throw new InvalidAccountException("Target account is not linked to this patient profile");
        }
    }

    private static void requireActivePatientAccount(Account account) {
        if (!account.isActive() || !account.isEmailLoginEnabled()) {
            throw new InvalidAccountException("An active patient email account is required for credential mail operations");
        }
    }

    private static void requireSynchronizedEmail(String profileEmail, Account account) {
        if (!isValidEmail(profileEmail) || !profileEmail.trim().equalsIgnoreCase(account.getUsername())) {
            throw new InvalidAccountException("Patient account profile email must be synchronized before credential mail operations");
        }
    }

    public Account changeEmail(UUID actor, UUID accountId, String requestedEmail, String reason) {
        if (actor == null || reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new InvalidAccountException("An admin and a reason of at most 500 characters are required");
        }
        String newEmail = normalizeEmail(requestedEmail);
        Account account = accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        if (account.getRole() == Role.SYSTEM) throw new InvalidAccountException("Service account email cannot be changed");
        String oldEmail = account.getUsername();
        if (newEmail.equalsIgnoreCase(oldEmail)) return account;
        if (accounts.existsByEmail(newEmail)) throw new InvalidAccountException("Email is already linked to another account");

        account.changeEmail(newEmail);
        Account saved = accounts.save(account);
        accounts.incrementCredentialVersion(accountId);
        recoveryTokens.invalidateUnused(accountId, Instant.now());
        emailChanges.record(accountId, actor, isValidEmail(oldEmail) ? oldEmail : null,
                newEmail, reason.trim());

        if (account.getStaffId() != null) {
            Staff owner = staff.findByIdForUpdate(account.getStaffId())
                    .orElseThrow(() -> new InvalidAccountException("Linked staff profile was not found"));
            owner.update(owner.getFullName(), owner.getJobTitle(), owner.getSpecialization(), owner.getLicenseNumber(),
                    owner.getPhoneNumber(), newEmail);
            staff.save(owner);
        } else if (account.getPatientId() != null) {
            emailChanges.queuePatientProfileSync(accountId, account.getPatientId(), newEmail);
        }

        var rendered = templates.emailChanged(displayName(account), newEmail,
                templates.publicWebUrl() + "/account-access?purpose=login");
        long version = accounts.credentialVersion(accountId);
        String opBase = "email-change:" + accountId + ":" + version;
        if (isValidEmail(oldEmail) && !oldEmail.equalsIgnoreCase(newEmail)) {
            mail.enqueue(new SecurityMailStore.NewMessage(accountId, fromEmail, oldEmail,
                    "ACCOUNT_EMAIL_CHANGED", opBase + ":old", rendered.subject(), rendered.textBody(), rendered.htmlBody()));
        }
        mail.enqueue(new SecurityMailStore.NewMessage(accountId, fromEmail, newEmail,
                "ACCOUNT_EMAIL_CHANGED", opBase + ":new", rendered.subject(), rendered.textBody(), rendered.htmlBody()));
        return saved;
    }

    public ForgotPasswordResult forgotPassword(String email) {
        Instant now = Instant.now();
        Instant windowStart = Instant.ofEpochSecond((now.getEpochSecond() / 900) * 900);
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String hashedAddress = sha256("recovery:" + normalized);
        if (mail.incrementRecoveryRequest(hashedAddress, windowStart) > 3 || !isValidEmail(normalized)) {
            return new ForgotPasswordResult(true);
        }
        Account account = accounts.findByUsername(normalized).orElse(null);
        if (account == null || !account.isActive() || account.getRole() == Role.SYSTEM
                || !account.isEmailLoginEnabled()) return new ForgotPasswordResult(true);

        // Serialize concurrent recovery requests and all credential-changing operations by account.
        account = accounts.findByIdForUpdate(account.getAccountId()).orElse(null);
        if (account == null || !account.isActive() || account.getRole() == Role.SYSTEM
                || !account.isEmailLoginEnabled() || !account.getUsername().equalsIgnoreCase(normalized)) {
            return new ForgotPasswordResult(true);
        }

        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32));
        String tokenHash = sha256(token);
        recoveryTokens.invalidateUnused(account.getAccountId(), now);
        recoveryTokens.save(UUID.randomUUID(), account.getAccountId(), tokenHash,
                accounts.credentialVersion(account.getAccountId()), now.plusSeconds(30 * 60));
        String link = templates.publicWebUrl() + "/account-access?purpose=reset&token="
                + java.net.URLEncoder.encode(token, StandardCharsets.UTF_8);
        var rendered = templates.recovery(displayName(account), link);
        mail.enqueue(new SecurityMailStore.NewMessage(account.getAccountId(), fromEmail, normalized,
                "PASSWORD_RECOVERY", "recovery:" + tokenHash, rendered.subject(), rendered.textBody(), rendered.htmlBody()));
        return new ForgotPasswordResult(true);
    }

    public RecoveryResult resetPassword(String token, String newPassword) {
        validatePassword(newPassword);
        if (token == null || token.isBlank() || token.length() > 256) throw invalidRecoveryToken();
        String tokenHash = sha256(token);
        var candidate = recoveryTokens.find(tokenHash).orElseThrow(this::invalidRecoveryToken);
        Account account = accounts.findByIdForUpdate(candidate.accountId())
                .orElseThrow(this::invalidRecoveryToken);
        var recovery = recoveryTokens.findForUpdate(tokenHash).orElseThrow(this::invalidRecoveryToken);
        Instant now = Instant.now();
        if (!recovery.accountId().equals(account.getAccountId())
                || recovery.usedAt() != null || !recovery.expiresAt().isAfter(now)) throw invalidRecoveryToken();
        if (!account.isActive() || accounts.credentialVersion(account.getAccountId()) != recovery.credentialVersion()) {
            throw invalidRecoveryToken();
        }
        if (passwords.matches(newPassword, account.getPasswordHash())) {
            throw new InvalidAccountException("New password must differ from the current password");
        }
        account.changePassword(passwords.hash(newPassword));
        accounts.save(account);
        accounts.incrementCredentialVersion(account.getAccountId());
        if (!recoveryTokens.markUsed(recovery.tokenId(), now)) throw invalidRecoveryToken();
        recoveryTokens.invalidateUnused(account.getAccountId(), now);
        var rendered = templates.passwordChanged(displayName(account));
        mail.enqueue(new SecurityMailStore.NewMessage(account.getAccountId(), fromEmail, account.getUsername(),
                "PASSWORD_CHANGED", "recovery-confirmed:" + tokenHash, rendered.subject(),
                rendered.textBody(), rendered.htmlBody()));
        return new RecoveryResult(true);
    }

    private MailDeliveryDTO deliveryForIssue(CreateAccountUseCase.CreationResult creation,
            String operation, String displayName, String email) {
        var previous = mail.byOperationKey(operation);
        if (previous.isPresent()) return previous.get();
        if (creation.temporaryPassword() == null) {
            throw new IllegalStateException("The original account email receipt is missing");
        }
        var rendered = templates.credential(displayName, email, creation.temporaryPassword());
        return mail.enqueue(new SecurityMailStore.NewMessage(creation.account().getAccountId(), fromEmail,
                email, "ACCOUNT_ISSUED", operation, rendered.subject(), rendered.textBody(), rendered.htmlBody()));
    }

    private String displayName(Account account) {
        if (account.getStaffId() != null) return staff.findById(account.getStaffId()).map(Staff::getFullName).orElse("bạn");
        if (account.getPatientId() != null) {
            try { return patients.displayName(account.getPatientId()); } catch (RuntimeException ignored) { return "bạn"; }
        }
        return "bạn";
    }

    private static String normalizeEmail(String value) {
        if (value == null || !value.equals(value.trim()) || !isValidEmail(value)) {
            throw new InvalidAccountException("Hồ sơ chưa có email hợp lệ để đăng nhập và nhận mật khẩu");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static boolean isValidEmail(String value) {
        return value != null && value.length() <= 254 && value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    private static void validatePassword(String value) {
        if (value == null || value.length() < 8 || value.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidAccountException("Password must contain at least 8 and at most 72 UTF-8 bytes");
        }
    }

    private InvalidAccountException invalidRecoveryToken() {
        return new InvalidAccountException("Recovery link is invalid, expired, or already used");
    }

    private static String randomPassword() {
        char[] value = new char[20];
        for (int i = 0; i < value.length; i++) value[i] = PASSWORD_CHARS[RANDOM.nextInt(PASSWORD_CHARS.length)];
        return new String(value);
    }

    private static byte[] randomBytes(int length) { byte[] bytes = new byte[length]; RANDOM.nextBytes(bytes); return bytes; }

    private static String operationKey(String scope, UUID actor, String key) {
        if (actor == null || key == null || key.isBlank() || key.length() > 160) {
            throw new IllegalArgumentException("Actor and a valid Idempotency-Key are required");
        }
        return scope + ":" + sha256(actor + ":" + key);
    }

    private static Role roleFor(JobTitle title) {
        if (title == null) throw new InvalidAccountException("Staff profile has no job title");
        return switch (title) {
            case DOCTOR -> Role.DOCTOR;
            case NURSE -> Role.NURSE;
            case TECHNICIAN -> Role.LAB_TECH;
            case PHARMACIST -> Role.PHARMACIST;
            case CASHIER -> Role.CASHIER;
            case MANAGER -> Role.MANAGER;
            case ADMINISTRATIVE -> Role.ADMIN;
        };
    }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception unavailable) { throw new IllegalStateException("SHA-256 is unavailable", unavailable); }
    }

    public record IssueResult(Account account, boolean replayed, MailDeliveryDTO mailDelivery) { }
    public record ForgotPasswordResult(boolean accepted) { }
    public record RecoveryResult(boolean success) { }
}
