package com.mediflow.patient.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.mediflow.patient.domain.exception.InvalidPatientDataException;

/** Patient aggregate. All state changes are validated here, independently of HTTP validation. */
public final class Patient {

    private final UUID patientId;
    private String fullName;
    private LocalDate dateOfBirth;
    private Gender gender;
    private final String identityNumber;
    private String address;
    private String phoneNumber;
    private String email;
    private String healthInsuranceNumber;
    private final Instant createdAt;
    private Instant updatedAt;

    private Patient(
            UUID patientId,
            String fullName,
            LocalDate dateOfBirth,
            Gender gender,
            String identityNumber,
            String address,
            String phoneNumber,
            String email,
            String healthInsuranceNumber,
            Instant createdAt,
            Instant updatedAt) {
        this.patientId = patientId;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.identityNumber = identityNumber;
        this.address = address;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.healthInsuranceNumber = healthInsuranceNumber;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Rehydrates a row already validated by the Patient schema. */
    public static Patient restore(
            UUID patientId,
            String fullName,
            LocalDate dateOfBirth,
            Gender gender,
            String identityNumber,
            String address,
            String phoneNumber,
            String email,
            String healthInsuranceNumber,
            Instant createdAt,
            Instant updatedAt) {
        if (patientId == null) {
            throw new IllegalArgumentException("patientId is required");
        }
        return new Patient(
                patientId,
                fullName,
                dateOfBirth,
                gender,
                identityNumber,
                address,
                phoneNumber,
                email,
                healthInsuranceNumber,
                createdAt,
                updatedAt);
    }

    public static Patient create(
            String fullName,
            LocalDate dateOfBirth,
            Gender gender,
            String identityNumber,
            String address,
            String phoneNumber,
            String email,
            String healthInsuranceNumber) {
        validateFullName(fullName);
        validateDateOfBirth(dateOfBirth);
        validateGender(gender);
        validateIdentityNumber(identityNumber);
        validateOptionalFields(phoneNumber, email, healthInsuranceNumber);
        Instant now = Instant.now();
        return new Patient(UUID.randomUUID(), fullName.trim(), dateOfBirth, gender, identityNumber.trim(),
                address, normalize(phoneNumber), normalize(email), normalize(healthInsuranceNumber), now, now);
    }

    /** Updates mutable demographics; identityNumber deliberately remains unchanged. */
    public void update(
            String fullName,
            LocalDate dateOfBirth,
            Gender gender,
            String address,
            String phoneNumber,
            String email,
            String healthInsuranceNumber) {
        validateFullName(fullName);
        validateDateOfBirth(dateOfBirth);
        validateGender(gender);
        validateOptionalFields(phoneNumber, email, healthInsuranceNumber);
        this.fullName = fullName.trim();
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.address = address;
        this.phoneNumber = normalize(phoneNumber);
        this.email = normalize(email);
        this.healthInsuranceNumber = normalize(healthInsuranceNumber);
        this.updatedAt = Instant.now();
    }

    private static void validateFullName(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("PATIENT_HOTEN_REQUIRED", "Họ tên không được để trống");
        }
        if (value.trim().length() > 100) {
            throw invalid("PATIENT_HOTEN_REQUIRED", "Họ tên không được vượt quá 100 ký tự");
        }
    }

    private static void validateDateOfBirth(LocalDate value) {
        if (value == null || value.isAfter(LocalDate.now())) {
            throw invalid("PATIENT_NGAYSINH_FUTURE", "Ngày sinh không được ở tương lai");
        }
    }

    private static void validateGender(Gender value) {
        if (value == null) {
            throw invalid("PATIENT_GIOITINH_REQUIRED", "Giới tính không được để trống");
        }
    }

    private static void validateIdentityNumber(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 20) {
            throw invalid("PATIENT_SOCMND_REQUIRED", "Số CMND/CCCD không hợp lệ");
        }
    }

    private static void validateOptionalFields(String phone, String email, String insurance) {
        if (phone != null && !phone.isBlank() && !phone.matches("\\d{10,15}")) {
            throw invalid("PATIENT_SDT_INVALID", "Số điện thoại phải gồm 10 đến 15 chữ số");
        }
        if (email != null && !email.isBlank()
                && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw invalid("PATIENT_EMAIL_INVALID", "Email không đúng định dạng");
        }
        if (insurance != null && !insurance.isBlank()
                && !insurance.matches("\\d{2}-\\d{8}-\\d")) {
            throw invalid("PATIENT_BHYT_INVALID", "Số BHYT không đúng định dạng");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static InvalidPatientDataException invalid(String code, String message) {
        return new InvalidPatientDataException(code, message);
    }

    public UUID patientId() {
        return patientId;
    }

    public String fullName() {
        return fullName;
    }

    public LocalDate dateOfBirth() {
        return dateOfBirth;
    }

    public Gender gender() {
        return gender;
    }

    public String identityNumber() {
        return identityNumber;
    }

    public String address() {
        return address;
    }

    public String phoneNumber() {
        return phoneNumber;
    }

    public String email() {
        return email;
    }

    public String healthInsuranceNumber() {
        return healthInsuranceNumber;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
