package com.mediflow.clinical.infrastructure.persistence.jpaEntity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "attached_result")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachedResultJpaEntity {

    @EmbeddedId
    private AttachedResultId id;

    @Column(name = "summary")
    private String summary;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
