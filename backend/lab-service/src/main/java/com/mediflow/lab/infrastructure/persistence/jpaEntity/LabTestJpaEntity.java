package com.mediflow.lab.infrastructure.persistence.jpaEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mediflow.lab.domain.model.LabTestStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lab_test")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class LabTestJpaEntity {
    @Id @Column(name = "test_id", nullable = false) private UUID testId;
    @Column(name = "record_id", nullable = false) private UUID recordId;
    @Column(name = "patient_id", nullable = false) private UUID patientId;
    @Column(name = "requesting_department_id", nullable = false) private UUID requestingDepartmentId;
    @Column(name = "test_type", nullable = false, length = 50) private String testType;
    @Column(name = "requested_date", nullable = false) private LocalDate requestedDate;
    @Column(name = "performed_date") private LocalDate performedDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private LabTestStatus status;
    @Column(columnDefinition = "text") private String conclusion;
    @Column(name = "is_paid", nullable = false) private boolean paid;
    @Builder.Default
    @OneToMany(mappedBy = "test", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LabResultJpaEntity> results = new ArrayList<>();
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;
}
