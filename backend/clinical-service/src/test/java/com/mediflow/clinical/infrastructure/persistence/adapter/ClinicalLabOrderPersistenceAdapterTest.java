package com.mediflow.clinical.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.clinical.domain.model.CareEpisodeType;
import com.mediflow.clinical.infrastructure.persistence.jpaEntity.ClinicalLabOrderJpaEntity;
import com.mediflow.clinical.infrastructure.persistence.repository.ClinicalLabOrderJpaRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** A withdrawn order no longer exists for closure, listing or Lab's context lookup. */
class ClinicalLabOrderPersistenceAdapterTest {
    private static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");
    private final ClinicalLabOrderJpaRepository repository = mock(ClinicalLabOrderJpaRepository.class);
    private final ClinicalLabOrderPersistenceAdapter adapter = new ClinicalLabOrderPersistenceAdapter(repository);
    private final UUID record = UUID.randomUUID();

    private ClinicalLabOrderJpaEntity entity(Instant withdrawnAt) {
        return ClinicalLabOrderJpaEntity.builder().sourceOrderId(UUID.randomUUID()).recordId(record)
                .patientId(UUID.randomUUID()).requestingDoctorId(UUID.randomUUID()).requestingDepartmentId(UUID.randomUUID())
                .careEpisodeType(CareEpisodeType.OUTPATIENT_VISIT).careEpisodeId(UUID.randomUUID()).labType("CBC")
                .requestedDate(LocalDate.of(2026, 11, 10)).priceCode("CBC_PRICE").orderedAt(NOW)
                .withdrawnAt(withdrawnAt).withdrawnByStaffId(withdrawnAt == null ? null : UUID.randomUUID())
                .withdrawalReason(withdrawnAt == null ? null : "Đổi ý").build();
    }

    @Test void withdrawnOrderIsNotFoundById() {
        ClinicalLabOrderJpaEntity withdrawn = entity(NOW);
        when(repository.findById(withdrawn.getSourceOrderId())).thenReturn(Optional.of(withdrawn));
        assertThat(adapter.findById(withdrawn.getSourceOrderId())).isEmpty();
    }

    @Test void withdrawnOrderIsLeftOutOfRecordList() {
        ClinicalLabOrderJpaEntity active = entity(null);
        when(repository.findByRecordIdOrderByOrderedAtAscSourceOrderIdAsc(record)).thenReturn(List.of(entity(NOW), active));
        assertThat(adapter.findByRecordId(record)).extracting(order -> order.sourceOrderId())
                .containsExactly(active.getSourceOrderId());
    }

    @Test void withdrawStoresWhoWhyAndWhen() {
        ClinicalLabOrderJpaEntity active = entity(null);
        UUID doctor = UUID.randomUUID();
        when(repository.findById(active.getSourceOrderId())).thenReturn(Optional.of(active));
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        adapter.withdraw(active.getSourceOrderId(), doctor, "Đổi ý", NOW);
        ArgumentCaptor<ClinicalLabOrderJpaEntity> saved = ArgumentCaptor.forClass(ClinicalLabOrderJpaEntity.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getWithdrawnAt()).isEqualTo(NOW);
        assertThat(saved.getValue().getWithdrawnByStaffId()).isEqualTo(doctor);
        assertThat(saved.getValue().getWithdrawalReason()).isEqualTo("Đổi ý");
    }
}
