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

        private final String username;

    /**
     *
     *
     *
     * $2a$10$...
     *
     *
     */
    private String passwordHash;

    /**
     *
     *
     * Staff account:
     *
     * SYSTEM:
     */
    private final UUID staffId;

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
        validate(username, passwordHash, role, staffId);

        this.accountId = accountId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.staffId = staffId;
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
        Instant now = Instant.now();

        return new Account(
                accountId,
                username,
                passwordHash,
                staffId,
                role,
                true,
                null,
                now,
                now);
    }

    private void validate(
            String username,
            String passwordHash,
            Role role,
            UUID staffId) {
        if (username == null
                || username.length() < 3
                || username.length() > 50
                || !username.matches("^[a-zA-Z0-9._-]+$")) {
            throw new InvalidAccountException(
                    "Username must contain 3 to 50 letters, digits, dots, underscores, or hyphens");
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
        if (role == Role.PATIENT && staffId != null) {
            throw new InvalidAccountException(
                    "PATIENT account must not have staffId");
        }

        /*
         *
         */
        if (role != Role.PATIENT
                && role != Role.SYSTEM
                && staffId == null) {

            throw new InvalidAccountException(
                    "Staff account must have staffId");
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

    public String getPasswordHash() {
        return passwordHash;
    }

    public UUID getStaffId() {
        return staffId;
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
