package com.mediflow.clinical.infrastructure.persistence.jpaEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.mediflow.clinical.domain.model.AppointmentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "appointment")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AppointmentJpaEntity {
    @Id @Column(name = "appointment_id", nullable = false) private UUID appointmentId;
    @Column(name = "patient_id", nullable = false) private UUID patientId;
    @Column(name = "doctor_id", nullable = false) private UUID doctorId;
    @Column(name = "department_id", nullable = false) private UUID departmentId;
    @Column(name = "appointment_date", nullable = false) private LocalDate appointmentDate;
    @Column(name = "appointment_time", nullable = false) private LocalTime appointmentTime;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private AppointmentStatus status;
    @Column(columnDefinition = "text") private String reason;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;
}
