package com.mediflow.pharmacy.infrastructure.persistence.jpaEntity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "PRESCRIPTION_LINE")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class PrescriptionLineJpaEntity {
 @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "line_id", updatable = false, nullable = false)
    private UUID lineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescription_id", nullable = false)
    private PrescriptionJpaEntity prescription;

    @Column(name = "drug_id", nullable = false)
    private UUID drugId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", precision = 15, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "dosage", length = 255)
    private String dosage;

    @Column(name = "line_total", precision = 15, scale = 2, nullable = false)
    private BigDecimal lineTotal;
}
