package com.mediflow.clinical.infrastructure.persistence.jpaEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "medical_record")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MedicalRecordJpaEntity {
    @Id @Column(name = "record_id", nullable = false) private UUID recordId;
    @Column(name = "patient_id", nullable = false) private UUID patientId;
    @Column(name = "doctor_id", nullable = false) private UUID doctorId;
    @Column(name = "department_id", nullable = false) private UUID departmentId;
    @Column(name = "examination_date", nullable = false) private LocalDate examinationDate;
    @Column(name = "symptoms", columnDefinition = "text") private String symptoms;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id") private AppointmentJpaEntity appointment;
    @Builder.Default
    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DiagnosisJpaEntity> diagnoses = new ArrayList<>();
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;
}
