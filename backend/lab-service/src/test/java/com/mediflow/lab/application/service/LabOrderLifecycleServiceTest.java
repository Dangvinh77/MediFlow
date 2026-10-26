package com.mediflow.lab.application.service;

import com.mediflow.lab.application.port.out.LabTestRepositoryPort;
import com.mediflow.lab.domain.model.CareEpisodeType;
import com.mediflow.lab.domain.model.LabTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LabOrderLifecycleServiceTest {
    private final LabTestRepositoryPort tests = mock(LabTestRepositoryPort.class);
    private final Instant now = Instant.parse("2026-10-26T01:00:00Z");
    private final LabOrderLifecycleService service = new LabOrderLifecycleService(tests, Clock.fixed(now, ZoneOffset.UTC));

    @Test void missingSourceIsExplicitAbsenceWithoutFabricatedTargetOrCompletion() {
        UUID id = UUID.randomUUID(); when(tests.findBySourceOrderId(id)).thenReturn(Optional.empty());
        var result = service.lookup(id);
        assertThat(result.exists()).isFalse(); assertThat(result.sourceOrderId()).isEqualTo(id);
        assertThat(result.testId()).isNull(); assertThat(result.status()).isNull(); assertThat(result.observedAt()).isEqualTo(now);
    }

    @Test void returnsExactPersistedSourceAndEpisodeWithCurrentLifecycle() {
        UUID record = UUID.randomUUID(), patient = UUID.randomUUID(), department = UUID.randomUUID(), episode = UUID.randomUUID(), order = UUID.randomUUID();
        LabTest test = LabTest.createV2(record, patient, department, order,
                CareEpisodeType.OUTPATIENT_VISIT, episode, "CBC", "CBC", LocalDate.of(2026, 10, 26));
        when(tests.findBySourceOrderId(order)).thenReturn(Optional.of(test));
        var result = service.lookup(order);
        assertThat(result.exists()).isTrue(); assertThat(result.testId()).isEqualTo(test.getTestId());
        assertThat(result.recordId()).isEqualTo(record); assertThat(result.patientId()).isEqualTo(patient);
        assertThat(result.departmentId()).isEqualTo(department); assertThat(result.careEpisodeId()).isEqualTo(episode);
        assertThat(result.careEpisodeType()).isEqualTo("OUTPATIENT_VISIT");
        assertThat(result.status()).isEqualTo("AWAITING_PAYMENT");
    }
}
