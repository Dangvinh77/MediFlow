package com.mediflow.billing.infrastructure.persistence.jpaEntity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity của bảng {@code LAB_TEST_TYPE} — bảng chiếu local {@code (labId -> labType)}.
 *
 * <p>Event {@code lab.result.created} không mang {@code labType} (04-lab.md §8) nhưng billing cần
 * nó để tra giá phí LAB. Consumer {@code lab.request.created} (Phần 5/5, payload này CÓ
 * {@code labType}) ghi vào bảng này; {@code FeeAccrualService} tra qua {@code LabTestTypePort}.
 * KHÔNG gọi REST đồng bộ sang lab-service. Xem THELOC-INTEGRATION-FOLLOWUP.md mục 2.
 */
@Entity
@Table(name = "LAB_TEST_TYPE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabTestTypeJpaEntity {

    @Id
    @Column(name = "lab_id", updatable = false, nullable = false)
    private UUID labId;

    @Column(name = "lab_type", length = 100, nullable = false)
    private String labType;

    @CreationTimestamp
    @Column(name = "recorded_at", updatable = false, nullable = false)
    private Instant recordedAt;
}
