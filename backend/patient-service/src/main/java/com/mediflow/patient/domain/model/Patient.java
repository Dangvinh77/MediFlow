package com.mediflow.patient.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Readable patient aggregate. Persistence and HTTP concerns stay outside this class. */
public final class Patient {

    private final UUID patientId;
    private final String fullName;
    private final LocalDate dateOfBirth;
    private final Gender gender;
    private final String identityNumber;
    private final String address;
    private final String phoneNumber;
    private final String email;
    private final String healthInsuranceNumber;
    private final Instant createdAt;
    private final Instant updatedAt;

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
