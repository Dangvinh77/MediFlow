package com.mediflow.lab.application.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.lab.application.dto.response.LabTestDTO;
import com.mediflow.lab.domain.model.LabResult;
import com.mediflow.lab.domain.model.LabTest;
import com.mediflow.lab.domain.model.LabTestStatus;

class LabTestDtoMapperTest {

    private final LabTestDtoMapper mapper = new LabTestDtoMapperImpl();

    @Test
    void toDto_mapsAggregateScalarsAndNestedResults() {
        UUID testId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-01T08:00:00Z");
        Instant updatedAt = Instant.parse("2026-09-01T09:00:00Z");
        LabResult result = LabResult.restore(UUID.randomUUID(), "glucose", "<0.01", "mmol/L", "3.9-5.6");
        LabTest test = LabTest.restore(testId, recordId, patientId, departmentId, "CBC",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), LabTestStatus.COMPLETED,
                "Âm tính", true, List.of(result), createdAt, updatedAt);

        LabTestDTO dto = mapper.toDto(test);

        assertThat(dto.testId()).isEqualTo(testId);
        assertThat(dto.recordId()).isEqualTo(recordId);
        assertThat(dto.patientId()).isEqualTo(patientId);
        assertThat(dto.requestingDepartmentId()).isEqualTo(departmentId);
        assertThat(dto.labType()).isEqualTo("CBC");
        assertThat(dto.status()).isEqualTo(LabTestStatus.COMPLETED);
        assertThat(dto.paid()).isTrue();
        assertThat(dto.results()).singleElement()
                .satisfies(mapped -> {
                    assertThat(mapped.resultId()).isEqualTo(result.getResultId());
                    assertThat(mapped.value()).isEqualTo("<0.01");
                });
    }

    @Test
    void toDto_resultList_isDefensivelyCopied() {
        LabResult result = LabResult.create("protein", "3+", null, null);
        LabTest test = LabTest.restore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Urinalysis", LocalDate.of(2026, 9, 1), null, LabTestStatus.PENDING,
                null, false, List.of(result), null, null);

        LabTestDTO dto = mapper.toDto(test);

        assertThat(dto.results()).isUnmodifiable();
    }
}
