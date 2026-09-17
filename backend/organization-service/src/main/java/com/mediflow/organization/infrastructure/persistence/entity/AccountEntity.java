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

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "staff_id")
    private UUID staffId;

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
