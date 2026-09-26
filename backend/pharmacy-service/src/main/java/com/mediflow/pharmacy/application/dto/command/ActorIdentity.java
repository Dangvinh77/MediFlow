package com.mediflow.pharmacy.application.dto.command;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import com.mediflow.pharmacy.domain.model.DispenseActor;

/**
 * Authenticated identity crossing the driving-adapter/application boundary.
 *
 * <p>{@code accountId} is the JWT subject and is never treated as a staff id. A staff id is
 * optional until the Gateway contract supplies the signed {@code staffId} claim. Use
 * {@link #requireStaffId()} for business operations whose ownership is defined by staff identity.
 *
 * @param accountId JWT subject account identifier
 * @param staffId signed staff identifier, when available
 * @param role normalized role name
 */
public record ActorIdentity(UUID accountId, UUID staffId, String role) {

    /** Creates a validated identity snapshot from signed JWT claims. */
    public ActorIdentity {
        Objects.requireNonNull(accountId, "accountId is required");
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role is required");
        }
        role = role.trim().toUpperCase(Locale.ROOT);
    }

    /** Returns whether this account has the administrative role.
     * @return {@code true} when role is ADMIN
     */
    public boolean isAdministrator() {
        return "ADMIN".equals(role);
    }

    /** Returns whether this identity represents a technical system actor.
     * @return {@code true} when role is SYSTEM
     */
    public boolean isSystem() {
        return "SYSTEM".equals(role);
    }

    /** Returns the staff id or fails closed when the signed claim is unavailable.
     * @return signed staff identifier
     */
    public UUID requireStaffId() {
        if (staffId == null) {
            throw new IllegalStateException("STAFF_ID_REQUIRED");
        }
        return staffId;
    }

    /**
     * Returns the identifier allowed to own an audit record.
     * Staff-backed operations fail closed when the signed staff claim is missing;
     * technical/admin operations retain accountId.
     *
     * @return identifier to persist as the audit actor
     */
    public UUID auditActorId() {
        if (staffId != null || isAdministrator() || isSystem()) {
            return staffId != null ? staffId : accountId;
        }
        throw new IllegalStateException("STAFF_ID_REQUIRED");
    }

    /** Resolves the explicit identity kind recorded when a human manually dispenses medication. */
    public DispenseActor dispenseAuditActor() {
        if (staffId != null) {
            return DispenseActor.staff(staffId);
        }
        if (isAdministrator()) {
            return DispenseActor.account(accountId);
        }
        throw new IllegalStateException("STAFF_ID_REQUIRED");
    }

    /** Keeps Spring Security's {@code Authentication#getName()} compatible with JWT subject. */
    @Override
    public String toString() {
        return accountId.toString();
    }
}
