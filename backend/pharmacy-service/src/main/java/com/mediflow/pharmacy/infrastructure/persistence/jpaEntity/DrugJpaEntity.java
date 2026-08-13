package com.mediflow.pharmacy.infrastructure.persistence.jpaEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "DRUG")
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class DrugJpaEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "drug_id", updatable = false, nullable = false)
  private UUID drugId;
  @Column(name = "drug_name", nullable = false, length = 150)
  private String drugName;
  @Column(name = "active_ingredient", length = 150)
  private String activeIngredient;
  @Column(name = "unit", length = 20, nullable = false)
  private String unit;
  @Column(name = "price", precision = 15, scale = 2, nullable = false)
  private BigDecimal price;
  @Column(name = "stock_quantity", nullable = false)
  private int stockQuantity;
  @Column(name = "expiry_date", nullable = false)
  private LocalDate expiryDate;
  @Column(name = "manufacturer", length = 150)
  private String manufacturer;

  @Column(name = "low_stock_threshold", nullable = false)
  private int lowStockThreshold;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false, nullable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private Instant updatedAt;
}
