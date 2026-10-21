package com.mediflow.organization.application.service;

import java.time.Instant;
import java.util.UUID;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.mediflow.organization.application.event.StaffCreatedEvent;
import com.mediflow.organization.application.port.in.CreateStaffUseCase;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.EventPublisher;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.application.port.out.StaffCreationReceiptRepository;
import com.mediflow.organization.application.port.out.StaffCreationReceiptRepository.Receipt;
import com.mediflow.organization.domain.exception.DepartmentInactiveException;
import com.mediflow.organization.domain.exception.DepartmentNotFoundException;
import com.mediflow.organization.domain.exception.StaffCreateIdempotencyConflictException;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Staff;
import org.springframework.transaction.annotation.Transactional;

@Transactional("organizationTransactionManager")
public class CreateStaffService implements CreateStaffUseCase {

    private final StaffRepository staffRepository;
    private final DepartmentRepository departmentRepository;
    private final EventPublisher eventPublisher;
    private final CorrelationIdProvider correlationIds;
    private final StaffCreationReceiptRepository receipts;

    public CreateStaffService(
            StaffRepository staffRepository,
            DepartmentRepository departmentRepository,
            EventPublisher eventPublisher,
            CorrelationIdProvider correlationIds,
            StaffCreationReceiptRepository receipts) {

        this.staffRepository = staffRepository;
        this.departmentRepository = departmentRepository;
        this.eventPublisher = eventPublisher;
        this.correlationIds = correlationIds;
        this.receipts = receipts;
    }

    /** Compatibility constructor for direct application-layer tests. */
    public CreateStaffService(
            StaffRepository staffRepository,
            DepartmentRepository departmentRepository,
            EventPublisher eventPublisher) {
        this(staffRepository, departmentRepository, eventPublisher, UUID::randomUUID, null);
    }

    /** Compatibility constructor for callers that supply a correlation provider. */
    public CreateStaffService(
            StaffRepository staffRepository,
            DepartmentRepository departmentRepository,
            EventPublisher eventPublisher,
            CorrelationIdProvider correlationIds) {
        this(staffRepository, departmentRepository, eventPublisher, correlationIds, null);
    }

    @Override
    public Staff execute(
            String fullName,
            UUID departmentId,
            JobTitle jobTitle,
            String specialization,
            String licenseNumber,
            String phoneNumber,
            String email) {

        return create(fullName, departmentId, jobTitle, specialization, licenseNumber, phoneNumber, email);
    }

    @Override
    public CreationResult executeWithIdempotency(
            UUID actorAccountId,
            String idempotencyKey,
            String fullName,
            UUID departmentId,
            JobTitle jobTitle,
            String specialization,
            String licenseNumber,
            String phoneNumber,
            String email) {
        if (actorAccountId == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Actor and idempotency key are required");
        }
        if (receipts == null) throw new IllegalStateException("Idempotency receipt store is unavailable");

        receipts.lockKey(actorAccountId, idempotencyKey);
        String fingerprint = fingerprint(fullName, departmentId, jobTitle,
                specialization, licenseNumber, phoneNumber, email);
        var existing = receipts.find(actorAccountId, idempotencyKey);
        if (existing.isPresent()) {
            Receipt receipt = existing.get();
            if (!receipt.requestFingerprint().equals(fingerprint)) {
                throw new StaffCreateIdempotencyConflictException();
            }
            Staff staff = staffRepository.findById(receipt.staffId())
                    .orElseThrow(() -> new IllegalStateException("Staff creation receipt target is missing"));
            return new CreationResult(staff, true);
        }

        Staff staff = create(fullName, departmentId, jobTitle, specialization,
                licenseNumber, phoneNumber, email);
        receipts.save(new Receipt(actorAccountId, idempotencyKey, fingerprint, staff.getStaffId()));
        return new CreationResult(staff, false);
    }

    private Staff create(
            String fullName,
            UUID departmentId,
            JobTitle jobTitle,
            String specialization,
            String licenseNumber,
            String phoneNumber,
            String email) {
        Department department = departmentRepository
                .findByIdForUpdate(departmentId)
                .orElseThrow(() ->
                        new DepartmentNotFoundException(departmentId));

        if (!department.isActive()) {
            throw new DepartmentInactiveException(departmentId);
        }

        Staff staff = Staff.create(
                fullName,
                departmentId,
                jobTitle,
                specialization,
                licenseNumber,
                phoneNumber,
                email
        );

        Staff savedStaff = staffRepository.save(staff);

        eventPublisher.publishStaffCreated(
                new StaffCreatedEvent(
                        UUID.randomUUID(),
                        Instant.now(),
                        correlationIds.currentOrCreate().toString(),
                        savedStaff.getStaffId(),
                        savedStaff.getFullName(),
                        savedStaff.getDepartmentId(),
                        savedStaff.getJobTitle().name()
                )
        );

        return savedStaff;
    }

    private String fingerprint(String fullName, UUID departmentId, JobTitle jobTitle,
                               String specialization, String licenseNumber,
                               String phoneNumber, String email) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            for (String value : new String[] {
                    fullName,
                    departmentId == null ? null : departmentId.toString(),
                    jobTitle == null ? null : jobTitle.name(),
                    specialization, licenseNumber, phoneNumber, email}) {
                if (value == null) output.writeInt(-1);
                else {
                    byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
                    output.writeInt(encoded.length);
                    output.write(encoded);
                }
            }
            return HexFormat.of().formatHex(digest.digest(bytes.toByteArray()));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        } catch (IOException impossible) {
            throw new IllegalStateException("Unable to fingerprint staff request", impossible);
        }
    }

}
