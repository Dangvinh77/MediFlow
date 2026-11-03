package com.mediflow.organization.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import com.mediflow.organization.domain.model.Role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "account",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_account_username",
                        columnNames = "username")
        })
public class AccountEntity {

    @Id
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

      @Column(name = "username", nullable = false, length = 254)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "temporary_password_expires_at")
    private Instant temporaryPasswordExpiresAt;

    @Column(name = "email_migrated", nullable = false)
    private boolean emailMigrated;

    @Column(name = "staff_id")
    private UUID staffId;

    @Column(name = "patient_id")
    private UUID patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected AccountEntity() {
    }

    public AccountEntity(
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
                lastLoginAt, createdAt, updatedAt,
                username != null && username.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"));
    }

    public AccountEntity(
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
                lastLoginAt, createdAt, updatedAt,
                username != null && username.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"));
    }

    public AccountEntity(UUID accountId, String username, String passwordHash, UUID staffId,
            UUID patientId, Role role, boolean active, Instant lastLoginAt, Instant createdAt,
            Instant updatedAt, boolean emailMigrated) {
        this(accountId, username, passwordHash, staffId, patientId, role, active, lastLoginAt,
                createdAt, updatedAt, emailMigrated, null);
    }

    public AccountEntity(UUID accountId, String username, String passwordHash, UUID staffId,
            UUID patientId, Role role, boolean active, Instant lastLoginAt, Instant createdAt,
            Instant updatedAt, boolean emailMigrated, Instant temporaryPasswordExpiresAt) {
        this.accountId = accountId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.staffId = staffId;
        this.patientId = patientId;
        this.role = role;
        this.active = active;
        this.lastLoginAt = lastLoginAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.emailMigrated = emailMigrated;
        this.temporaryPasswordExpiresAt = temporaryPasswordExpiresAt;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getUsername() {
        return username;
    }

      public void setUsername(String username) { this.username = username; }

    public boolean isMustChangePassword() { return mustChangePassword; }
    public Instant getTemporaryPasswordExpiresAt() { return temporaryPasswordExpiresAt; }

    public boolean isEmailMigrated() { return emailMigrated; }

    public void requirePasswordChange() { this.mustChangePassword = true; }
    public void requirePasswordChangeUntil(Instant expiresAt) {
        this.mustChangePassword = true;
        this.temporaryPasswordExpiresAt = expiresAt;
    }

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
