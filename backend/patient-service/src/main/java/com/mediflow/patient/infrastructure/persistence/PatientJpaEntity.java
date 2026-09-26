package com.mediflow.patient.infrastructure.persistence;

import com.mediflow.patient.domain.model.Gender;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Persistence representation of the patient read model. */
@Entity
@Table(name = "PATIENT")
public class PatientJpaEntity {

    @Id
    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 1)
    private Gender gender;

    @Column(name = "identity_number", nullable = false, unique = true, length = 20)
    private String identityNumber;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "phone_number", length = 15)
    private String phoneNumber;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "health_insurance_number", length = 20)
    private String healthInsuranceNumber;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected PatientJpaEntity() {
    }

    public PatientJpaEntity(UUID patientId, String fullName, LocalDate dateOfBirth,
                            Gender gender, String identityNumber, String address,
                            String phoneNumber, String email, String healthInsuranceNumber,
                            Instant createdAt, Instant updatedAt) {
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

    public UUID getPatientId() { return patientId; }
    public String getFullName() { return fullName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public Gender getGender() { return gender; }
    public String getIdentityNumber() { return identityNumber; }
    public String getAddress() { return address; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getEmail() { return email; }
    public String getHealthInsuranceNumber() { return healthInsuranceNumber; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
