package com.mediflow.clinical.infrastructure.persistence.jpaEntity;

import com.mediflow.clinical.domain.model.CareEpisodeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "clinical_lab_order")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ClinicalLabOrderJpaEntity {
    @Id @Column(name = "source_order_id", nullable = false) private UUID sourceOrderId;
    @Column(name = "record_id", nullable = false) private UUID recordId;
    @Column(name = "patient_id", nullable = false) private UUID patientId;
    @Column(name = "requesting_doctor_id", nullable = false) private UUID requestingDoctorId;
    @Column(name = "requesting_department_id", nullable = false) private UUID requestingDepartmentId;
    @Enumerated(EnumType.STRING)
    @Column(name = "care_episode_type", nullable = false, length = 32) private CareEpisodeType careEpisodeType;
    @Column(name = "care_episode_id", nullable = false) private UUID careEpisodeId;
    @Column(name = "lab_type", nullable = false, length = 50) private String labType;
    @Column(name = "requested_date", nullable = false) private LocalDate requestedDate;
    @Column(name = "price_code", nullable = false, length = 64) private String priceCode;
    @Column(name = "ordered_at", nullable = false) private Instant orderedAt;
}
