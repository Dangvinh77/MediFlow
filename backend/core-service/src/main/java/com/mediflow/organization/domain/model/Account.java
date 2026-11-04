package com.mediflow.organization.domain.model;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.organization.domain.exception.InvalidAccountException;

/**
 *
 *
 * - username
 * - password hash
 * - role
 *
 *
 *
 */
public class Account {

        private final UUID accountId;

        private String username;

    /**
     *
     *
     *
     * $2a$10$...
     *
     *
     */
    private String passwordHash;

    private boolean emailLoginEnabled;

    private boolean mustChangePassword;
        private Instant temporaryPasswordExpiresAt;

    /**
     *
     *
     * Staff account:
     *
     * SYSTEM:
     */
        private final UUID staffId;

        /** Bare UUID reference to patient-service; never a cross-service JPA relation. */
        private final UUID patientId;

        private final Role role;

        private boolean active;

        private Instant lastLoginAt;

        private final Instant createdAt;

        private Instant updatedAt;

        public Account(
            UUID accountId,
            String username,
            String passwordHash,
            UUID staffId,
            Role role,
            boolean active,
            Instant lastLoginAt,
            Instant createdAt,
            Instant updatedAt) {
        this(accountId, username, passwordHash, staffId, null, role, active,
                lastLoginAt, createdAt, updatedAt, isEmail(username));
    }

        public Account(
            UUID accountId,
            String username,
            String passwordHash,
            UUID staffId,
            UUID patientId,
            Role role,
            boolean active,
            Instant lastLoginAt,
            Instant createdAt,
            Instant updatedAt) {
        this(accountId, username, passwordHash, staffId, patientId, role, active,
                lastLoginAt, createdAt, updatedAt, isEmail(username));
    }

    public Account(UUID accountId, String username, String passwordHash, UUID staffId, UUID patientId,
            Role role, boolean active, Instant lastLoginAt, Instant createdAt, Instant updatedAt,
            boolean emailLoginEnabled) {
        this(accountId, username, passwordHash, staffId, patientId, role, active, lastLoginAt,
                createdAt, updatedAt, emailLoginEnabled, null);
    }

    public Account(UUID accountId, String username, String passwordHash, UUID staffId, UUID patientId,
            Role role, boolean active, Instant lastLoginAt, Instant createdAt, Instant updatedAt,
            boolean emailLoginEnabled, Instant temporaryPasswordExpiresAt) {
        validate(username, passwordHash, role, staffId, patientId);

        this.accountId = accountId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.emailLoginEnabled = emailLoginEnabled;
        this.temporaryPasswordExpiresAt = temporaryPasswordExpiresAt;
        this.staffId = staffId;
        this.patientId = patientId;
        this.role = role;
        this.active = active;
        this.lastLoginAt = lastLoginAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

        public static Account create(
            UUID accountId,
            String username,
            String passwordHash,
            UUID staffId,
            Role role) {
        return create(accountId, username, passwordHash, staffId, null, role);
    }

        public static Account create(
            UUID accountId,
            String username,
            String passwordHash,
            UUID staffId,
            UUID patientId,
            Role role) {
        Instant now = Instant.now();

        return new Account(
                accountId,
                username,
                passwordHash,
                staffId,
                patientId,
                role,
                true,
                null,
                now,
                now,
                isEmail(username));
    }

    private void validate(
            String username,
            String passwordHash,
            Role role,
            UUID staffId,
            UUID patientId) {
        boolean email = username != null && username.length() <= 254
                && username.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
        boolean legacyServiceOrSeed = username != null && username.length() <= 50
                && username.matches("^[a-zA-Z0-9._-]+$");
        if (username == null || username.length() < 3 || (!email && !legacyServiceOrSeed)) {
            throw new InvalidAccountException("Username must be a valid email address");
        }

        if (passwordHash == null || passwordHash.isBlank()) {
            throw new InvalidAccountException(
                    "Password hash cannot be blank");
        }

        if (role == null) {
            throw new InvalidAccountException(
                    "Account role cannot be null");
        }

        /*
         */
        if (role == Role.PATIENT && (staffId != null || patientId == null)) {
            throw new InvalidAccountException(
                    "PATIENT account must have patientId and must not have staffId");
        }

        /*
         *
         */
        if (role != Role.PATIENT && role != Role.SYSTEM && (staffId == null || patientId != null)) {

            throw new InvalidAccountException(
                    "Staff account must have staffId");
        }

        if (role == Role.SYSTEM && (staffId != null || patientId != null)) {
            throw new InvalidAccountException(
                    "SYSTEM account must not have staffId or patientId");
        }
    }

        public void activate() {
        this.active = true;

        touch();
    }

        public void deactivate() {
        this.active = false;

        touch();
    }

        public void recordLogin() {

        /*
         */
        if (!active) {
            throw new InvalidAccountException(
                    "Inactive account cannot login");
        }

        this.lastLoginAt = Instant.now();

        touch();
    }

        public void changePassword(String newPasswordHash) {

        if (newPasswordHash == null
                || newPasswordHash.isBlank()) {

            throw new InvalidAccountException(
                    "Password hash cannot be blank");
        }

        this.passwordHash = newPasswordHash;
        this.mustChangePassword = false;
        this.temporaryPasswordExpiresAt = null;

        touch();
    }

        private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getUsername() {
        return username;
    }

    public void changeEmail(String email) {
        String normalized = email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized == null || normalized.length() > 254
                || !normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new InvalidAccountException("A valid account email is required");
        }
        this.username = normalized;
        this.emailLoginEnabled = true;
        touch();
    }

    private static boolean isEmail(String value) {
        return value != null && value.length() <= 254 && value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    public boolean isEmailLoginEnabled() { return emailLoginEnabled; }

    public boolean isMustChangePassword() { return mustChangePassword; }

    public void requirePasswordChange() { this.mustChangePassword = true; }

    public void requirePasswordChangeUntil(Instant expiresAt) {
        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            throw new InvalidAccountException("Temporary password expiry must be in the future");
        }
        this.mustChangePassword = true;
        this.temporaryPasswordExpiresAt = expiresAt;
    }

    /** Reconstitutes the stored mandatory-change expiry without applying a new TTL. */
    public void restoreTemporaryPasswordExpiry(Instant expiresAt) {
        this.mustChangePassword = true;
        this.temporaryPasswordExpiresAt = expiresAt;
    }

    public Instant getTemporaryPasswordExpiresAt() { return temporaryPasswordExpiresAt; }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UUID getStaffId() {
        return staffId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
