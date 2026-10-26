package com.mediflow.patient.infrastructure.persistence;

import com.mediflow.patient.application.port.out.PatientIdentityAuditPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Uses the Patient transaction; no identity values are written to application logs. */
@Repository
public class PatientIdentityAuditAdapter implements PatientIdentityAuditPort {
    @PersistenceContext(unitName = "patient")
    private EntityManager entityManager;

    @Override
    public void record(UUID patientId, String reason, String previousIdentityNumber, String currentIdentityNumber,
            LocalDate previousDateOfBirth, LocalDate currentDateOfBirth, Instant occurredAt, UUID correlationId) {
        var actor = SecurityContextHolder.getContext().getAuthentication();
        if (actor == null || !actor.isAuthenticated()) {
            throw new org.springframework.security.access.AccessDeniedException("Authenticated actor required for identity correction");
        }
        String role = actor.getAuthorities().stream().map(authority -> authority.getAuthority())
                .filter(value -> value.equals("ROLE_ADMIN") || value.equals("ROLE_NURSE"))
                .findFirst().orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Identity correction denied"));
        entityManager.createNativeQuery("""
                INSERT INTO patient_identity_audit (audit_id, patient_id, actor_account_id, actor_role, reason,
                    previous_identity_number, current_identity_number, previous_date_of_birth,
                    current_date_of_birth, occurred_at, correlation_id)
                VALUES (:id, :patient, :actor, :role, :reason, :oldIdentity, :newIdentity, :oldBirth, :newBirth, :at, :correlation)
                """)
                .setParameter("id", UUID.randomUUID()).setParameter("patient", patientId)
                .setParameter("actor", UUID.fromString(actor.getName())).setParameter("role", role.substring(5))
                .setParameter("reason", reason).setParameter("oldIdentity", previousIdentityNumber)
                .setParameter("newIdentity", currentIdentityNumber).setParameter("oldBirth", previousDateOfBirth)
                .setParameter("newBirth", currentDateOfBirth).setParameter("at", occurredAt)
                .setParameter("correlation", correlationId).executeUpdate();
    }
}
