package com.mediflow.clinical.domain.model;

import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class Appointment {
    public static final LocalTime OPENING_TIME = LocalTime.of(7, 0);
    public static final LocalTime CLOSING_TIME = LocalTime.of(17, 0);

    private final UUID appointmentId;
    private final UUID patientId;
    private final UUID doctorId;
    private final UUID departmentId;
    private LocalDate appointmentDate;
    private LocalTime appointmentTime;
    private AppointmentStatus status;
    private String reason;
    private final Instant createdAt;
    private Instant updatedAt;
    private short careContractVersion;
    private UUID examClearanceId;
    private Instant examClearanceAt;
    private UUID emergencyOverrideId;
    private String examPriceCode;
    private Instant checkedInAt;
    private Instant examinationStartedAt;
    private Instant completedAt;
    @Getter(AccessLevel.NONE)
    private final Clock clock;

    public static Appointment create(UUID patientId, UUID doctorId, UUID departmentId,
                                     LocalDate date, LocalTime time, String reason) {
        return create(patientId, doctorId, departmentId, date, time, reason, Clock.systemDefaultZone());
    }

    /** Clock overload makes appointment scheduling rules deterministic without coupling domain to Spring. */
    public static Appointment create(UUID patientId, UUID doctorId, UUID departmentId,
                                     LocalDate date, LocalTime time, String reason, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        validateReferences(patientId, doctorId, departmentId);
        validateSchedule(date, time, clock);
        return new Appointment(UUID.randomUUID(), patientId, doctorId, departmentId, date, time,
                AppointmentStatus.PENDING, reason, clock.instant(), null, (short) 0,
                null, null, null, null, null, null, null, clock);
    }

    /** Loading history must not reapply the new-appointment 'not in the past' rule. */
    public static Appointment restore(UUID id, UUID patientId, UUID doctorId, UUID departmentId,
                                      LocalDate date, LocalTime time, AppointmentStatus status,
                                      String reason, Instant createdAt, Instant updatedAt) {
        return restore(id, patientId, doctorId, departmentId, date, time, status, reason, createdAt, updatedAt,
                (short) 0, null, null, null, null, null, null, null);
    }

    public static Appointment restore(UUID id, UUID patientId, UUID doctorId, UUID departmentId,
                                      LocalDate date, LocalTime time, AppointmentStatus status,
                                      String reason, Instant createdAt, Instant updatedAt,
                                      short careContractVersion, UUID examClearanceId, Instant examClearanceAt,
                                      UUID emergencyOverrideId, String examPriceCode, Instant checkedInAt,
                                      Instant examinationStartedAt, Instant completedAt) {
        validateReferences(patientId, doctorId, departmentId);
        if (careContractVersion != 0 && careContractVersion != 1) {
            throw new InvalidClinicalDataException("CLINICAL_INVALID_STATUS_TRANSITION",
                    "Unsupported care contract version");
        }
        if (careContractVersion == 1 && (examPriceCode == null || examPriceCode.isBlank())) {
            throw new InvalidClinicalDataException("CLINICAL_INVALID_STATUS_TRANSITION",
                    "Version 1 appointments require an exam price code");
        }
        if ((status == AppointmentStatus.READY_FOR_EXAM
                || status == AppointmentStatus.IN_EXAM
                || status == AppointmentStatus.COMPLETED)
                && examClearanceId == null && emergencyOverrideId == null) {
            throw new InvalidClinicalDataException("CLINICAL_EXAM_CLEARANCE_REQUIRED",
                    "Exam clearance or emergency override is required");
        }
        return new Appointment(Objects.requireNonNull(id), patientId, doctorId, departmentId,
                Objects.requireNonNull(date), Objects.requireNonNull(time), Objects.requireNonNull(status),
                reason, Objects.requireNonNull(createdAt), updatedAt, careContractVersion, examClearanceId,
                examClearanceAt, emergencyOverrideId, examPriceCode, checkedInAt, examinationStartedAt,
                completedAt, Clock.systemDefaultZone());
    }

    public void update(LocalDate date, LocalTime time, String reason) {
        if (status != AppointmentStatus.PENDING) {
            throw new InvalidClinicalDataException("APPOINTMENT_INVALID_TRANSITION",
                    "Only a pending appointment can be updated");
        }
        validateSchedule(date, time, clock);
        this.appointmentDate = date;
        this.appointmentTime = time;
        this.reason = reason;
        this.updatedAt = clock.instant();
    }

    /** Legacy status command. V2 examination states are reachable only through their explicit commands. */
    public void changeStatus(AppointmentStatus next) {
        if (status != AppointmentStatus.PENDING
                || (next != AppointmentStatus.ARRIVED && next != AppointmentStatus.CANCELLED)) {
            String code = isCareFinanceState(next) || isCareFinanceState(status)
                    ? "CLINICAL_INVALID_STATUS_TRANSITION" : "APPOINTMENT_INVALID_TRANSITION";
            throw new InvalidClinicalDataException(code, "Invalid appointment status transition");
        }
        status = next;
        updatedAt = clock.instant();
    }

    /** Checks the patient in and atomically opens the purpose-scoped exam payment gate. */
    public void checkIn(String priceCode, Instant at) {
        if (status != AppointmentStatus.PENDING || careContractVersion != 0) {
            throw new InvalidClinicalDataException("CLINICAL_INVALID_STATUS_TRANSITION",
                    "Only a legacy pending appointment can enter the V2 check-in flow");
        }
        if (priceCode == null || priceCode.isBlank() || at == null) {
            throw new InvalidClinicalDataException("CLINICAL_INVALID_STATUS_TRANSITION",
                    "Check-in requires an exam price code and timestamp");
        }
        checkedInAt = at;
        examPriceCode = priceCode;
        careContractVersion = 1;
        status = AppointmentStatus.AWAITING_PAYMENT;
        updatedAt = at;
    }

    /** Stores an exact-target clearance and unlocks an appointment awaiting payment. */
    public void recordExamClearance(UUID clearanceId, Instant grantedAt) {
        if (clearanceId == null || grantedAt == null) {
            throw new InvalidClinicalDataException("CLINICAL_CLEARANCE_TARGET_MISMATCH",
                    "Exam clearance identity and grant time are required");
        }
        if (status != AppointmentStatus.ARRIVED
                && status != AppointmentStatus.AWAITING_PAYMENT
                && status != AppointmentStatus.READY_FOR_EXAM) {
            throw new InvalidClinicalDataException("CLINICAL_INVALID_STATUS_TRANSITION",
                    "Exam clearance cannot be applied in the current appointment state");
        }
        examClearanceId = clearanceId;
        examClearanceAt = grantedAt;
        if (status == AppointmentStatus.AWAITING_PAYMENT) {
            status = AppointmentStatus.READY_FOR_EXAM;
        }
        updatedAt = grantedAt;
    }

    public void startExam(UUID clearanceId, UUID overrideId, Instant at) {
        if (status != AppointmentStatus.ARRIVED
                && status != AppointmentStatus.AWAITING_PAYMENT
                && status != AppointmentStatus.READY_FOR_EXAM) {
            throw new InvalidClinicalDataException("CLINICAL_INVALID_STATUS_TRANSITION",
                    "Appointment is not ready to start an examination");
        }
        if (clearanceId == null && overrideId == null) {
            throw new InvalidClinicalDataException("CLINICAL_EXAM_CLEARANCE_REQUIRED",
                    "A matching exam clearance or audited emergency override is required");
        }
        if (careContractVersion != 1 || examPriceCode == null || examPriceCode.isBlank()) {
            throw new InvalidClinicalDataException("CLINICAL_INVALID_STATUS_TRANSITION",
                    "Version 1 exam state requires an exam price code");
        }
        if (at == null) {
            throw new InvalidClinicalDataException("CLINICAL_INVALID_STATUS_TRANSITION",
                    "Examination start time is required");
        }
        if (clearanceId != null) {
            examClearanceId = clearanceId;
        }
        emergencyOverrideId = overrideId;
        examinationStartedAt = at;
        status = AppointmentStatus.IN_EXAM;
        updatedAt = at;
    }

    /** Promotes an explicitly handled legacy arrival before a V2 examination is started. */
    public void promoteToCareFinanceV2(String priceCode, Instant at) {
        if (careContractVersion != 0
                || (status != AppointmentStatus.ARRIVED
                && status != AppointmentStatus.AWAITING_PAYMENT
                && status != AppointmentStatus.READY_FOR_EXAM)
                || priceCode == null || priceCode.isBlank() || at == null) {
            throw new InvalidClinicalDataException("CLINICAL_INVALID_STATUS_TRANSITION",
                    "A legacy arrived appointment requires a price code before V2 examination");
        }
        careContractVersion = 1;
        examPriceCode = priceCode;
        if (checkedInAt == null) {
            checkedInAt = at;
        }
        updatedAt = at;
    }

    public void complete(Instant at) {
        if (status != AppointmentStatus.IN_EXAM || at == null) {
            throw new InvalidClinicalDataException("CLINICAL_INVALID_STATUS_TRANSITION",
                    "Only an in-exam appointment can be completed");
        }
        status = AppointmentStatus.COMPLETED;
        completedAt = at;
        updatedAt = at;
    }

    /** BR-R4 compatibility helper. */
    public void markArrived() {
        changeStatus(AppointmentStatus.ARRIVED);
    }

    public boolean isPending() {
        return status == AppointmentStatus.PENDING;
    }

    private static boolean isCareFinanceState(AppointmentStatus value) {
        return value == AppointmentStatus.AWAITING_PAYMENT || value == AppointmentStatus.READY_FOR_EXAM
                || value == AppointmentStatus.IN_EXAM || value == AppointmentStatus.COMPLETED;
    }

    private static void validateReferences(UUID patientId, UUID doctorId, UUID departmentId) {
        if (patientId == null || doctorId == null || departmentId == null) {
            throw new InvalidClinicalDataException("APPOINTMENT_REF_REQUIRED",
                    "Patient, doctor and department are required");
        }
    }

    private static void validateSchedule(LocalDate date, LocalTime time, Clock clock) {
        if (date == null) {
            throw new InvalidClinicalDataException("APPOINTMENT_DATE_REQUIRED", "Appointment date is required");
        }
        if (date.isBefore(LocalDate.now(clock))) {
            throw new InvalidClinicalDataException("APPOINTMENT_PAST_DATE",
                    "Appointment date cannot be in the past");
        }
        if (time == null || time.isBefore(OPENING_TIME) || time.isAfter(CLOSING_TIME)) {
            throw new InvalidClinicalDataException("APPOINTMENT_TIME_OUT_OF_HOURS",
                    "Appointment time must be between 07:00 and 17:00");
        }
    }
}
