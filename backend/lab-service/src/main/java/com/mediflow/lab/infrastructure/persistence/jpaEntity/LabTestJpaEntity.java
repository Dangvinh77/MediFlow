package com.mediflow.lab.infrastructure.persistence.jpaEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mediflow.lab.domain.model.LabTestStatus;
import com.mediflow.lab.domain.model.CareEpisodeType;

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
    @Column(name = "care_contract_version", nullable = false) private short careContractVersion;
    @Enumerated(EnumType.STRING) @Column(name = "care_episode_type", length = 32) private CareEpisodeType careEpisodeType;
    @Column(name = "care_episode_id") private UUID careEpisodeId;
    @Column(name = "source_order_id") private UUID sourceOrderId;
    @Column(name = "price_code", length = 64) private String priceCode;
    @Column(name = "performed_date") private LocalDate performedDate;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 20) private LabTestStatus status;
    @Column(name = "conclusion", columnDefinition = "text") private String conclusion;
    @Column(name = "is_paid", nullable = false) private boolean paid;
    @Column(name = "clearance_id") private UUID clearanceId;
    @Column(name = "clearance_granted_at") private Instant clearanceGrantedAt;
    @Column(name = "emergency_override_id") private UUID emergencyOverrideId;
    @Column(name = "result_version", nullable = false) private int resultVersion;
    @Builder.Default
    @OneToMany(mappedBy = "test", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LabResultJpaEntity> results = new ArrayList<>();
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;
}
