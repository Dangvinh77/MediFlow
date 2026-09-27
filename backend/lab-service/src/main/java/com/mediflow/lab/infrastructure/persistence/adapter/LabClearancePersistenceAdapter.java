package com.mediflow.lab.infrastructure.persistence.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.lab.application.port.out.LabClearanceRepositoryPort;
import com.mediflow.lab.domain.model.LabFinancialClearance;
import com.mediflow.lab.infrastructure.persistence.jpaEntity.LabFinancialClearanceJpaEntity;
import com.mediflow.lab.infrastructure.persistence.repository.LabFinancialClearanceJpaRepository;
import com.mediflow.lab.infrastructure.persistence.repository.LabTestJpaRepository;
import com.mediflow.lab.infrastructure.persistence.repository.ProcessedEventJpaRepository;

import lombok.RequiredArgsConstructor;

/** Stores exact-target clearances and claims the source event in one database transaction. */
@Component
@RequiredArgsConstructor
public class LabClearancePersistenceAdapter implements LabClearanceRepositoryPort {

    private static final String ROUTING_KEY = "financial.clearance.granted";

    private final ProcessedEventJpaRepository processedEvents;
    private final LabTestJpaRepository tests;
    private final LabFinancialClearanceJpaRepository clearances;

    @Override
    @Transactional
    public boolean claimAndSave(UUID eventId, List<LabFinancialClearance> values) {
        if (processedEvents.insertIfAbsent(eventId, ROUTING_KEY) != 1) {
            return false;
        }
        List<LabFinancialClearanceJpaEntity> rows = values.stream().map(this::toEntity).toList();
        clearances.saveAllAndFlush(rows);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LabFinancialClearance> findValidByTestId(UUID testId, Instant at) {
        return clearances.findValidByTestId(testId, at).stream().findFirst().map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LabFinancialClearance> findLatestByTestId(UUID testId) {
        return clearances.findByTestTestIdOrderByGrantedAtDesc(testId).stream().findFirst().map(this::toDomain);
    }

    private LabFinancialClearanceJpaEntity toEntity(LabFinancialClearance value) {
        return LabFinancialClearanceJpaEntity.builder()
                .clearanceTargetId(value.clearanceTargetId())
                .clearanceId(value.clearanceId())
                .eventId(value.eventId())
                .invoiceId(value.invoiceId())
                .accountId(value.accountId())
                .test(tests.getReferenceById(value.testId()))
                .patientId(value.patientId())
                .careEpisodeType(value.careEpisodeType())
                .careEpisodeId(value.careEpisodeId())
                .amount(value.amount())
                .currency(value.currency())
                .expiresAt(value.expiresAt())
                .emergencyOverride(value.emergencyOverride())
                .grantedAt(value.grantedAt())
                .build();
    }

    private LabFinancialClearance toDomain(LabFinancialClearanceJpaEntity row) {
        return new LabFinancialClearance(row.getClearanceTargetId(), row.getClearanceId(), row.getEventId(),
                row.getInvoiceId(), row.getAccountId(), row.getTest().getTestId(), row.getPatientId(),
                row.getCareEpisodeType(), row.getCareEpisodeId(), com.mediflow.lab.domain.model.ClearancePurpose.LAB_TEST,
                row.getAmount(), row.getCurrency(), row.getExpiresAt(), row.isEmergencyOverride(), row.getGrantedAt());
    }
}
