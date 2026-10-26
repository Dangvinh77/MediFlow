package com.mediflow.organization.application.service;

import java.time.Instant;
import com.mediflow.organization.domain.exception.OwnStaffContactAccessDeniedException;
import com.mediflow.organization.application.event.StaffContactUpdatedEvent;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.application.port.out.EventPublisher;
import com.mediflow.organization.application.port.in.UpdateOwnStaffContactUseCase;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.exception.StaffNotFoundException;
import com.mediflow.organization.domain.exception.InvalidStaffDataException;
import com.mediflow.organization.domain.model.Staff;
import com.mediflow.organization.domain.model.StaffProfile;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Transactional("organizationTransactionManager")
public class UpdateOwnStaffContactService implements UpdateOwnStaffContactUseCase {
    private final StaffRepository staff;

    private final EventPublisher events;
    private final CorrelationIdProvider correlationIds;

    public UpdateOwnStaffContactService(StaffRepository staff,
            EventPublisher events,
            CorrelationIdProvider correlationIds) {
        this.staff = staff; this.events = events; this.correlationIds = correlationIds;
    }

    @Override
    public Staff update(UUID requestedStaffId, UUID signedStaffId, String phoneNumber, String email, String address) {
        if (signedStaffId == null || !signedStaffId.equals(requestedStaffId)) {
            throw new OwnStaffContactAccessDeniedException();
        }
        Staff current = staff.findByIdForUpdate(signedStaffId)
                .orElseThrow(() -> new StaffNotFoundException(signedStaffId));
        if (!current.isActive()) throw new InvalidStaffDataException("Inactive staff cannot update contact information");
        String phone = clean(phoneNumber);
        String mail = clean(email);
        if (mail != null && mail.length() > 100) throw new InvalidStaffDataException("Email must not exceed 100 characters");
        current.update(current.getFullName(), current.getJobTitle(), current.getSpecialization(),
                current.getLicenseNumber(), phone, mail);
        StaffProfile old = current.getProfile();
        current.withProfile(new StaffProfile(old.dateOfBirth(), old.gender(), address, old.hireDate(), old.qualification()));
        Staff saved = staff.save(current);
        events.publishStaffContactUpdated(new StaffContactUpdatedEvent(
                UUID.randomUUID(), Instant.now(), correlationIds.currentOrCreate().toString(), signedStaffId));
        return saved;
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
