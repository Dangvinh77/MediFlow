package com.mediflow.clinical.infrastructure.persistence.jpaEntity;

import java.io.Serializable;
import java.util.UUID;

import com.mediflow.clinical.domain.model.ExternalResultType;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class AttachedResultId implements Serializable {

    @Column(name = "record_id", nullable = false)
    private UUID recordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ExternalResultType type;

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;
}
