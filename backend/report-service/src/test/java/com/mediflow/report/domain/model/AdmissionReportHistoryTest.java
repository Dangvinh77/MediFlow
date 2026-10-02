package com.mediflow.report.domain.model;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import com.mediflow.report.domain.model.AdmissionReportFact.Kind;
import com.mediflow.report.domain.model.AdmissionReportHistory.Status;

class AdmissionReportHistoryTest {
    private final UUID admission = UUID.randomUUID(), patient = UUID.randomUUID(), department = UUID.randomUUID();
    private final Instant admittedAt = Instant.parse("2026-09-28T02:10:00.123456789Z");
    private final AdmissionReportFact start = new AdmissionReportFact(Kind.STARTED, admission, patient, department,
            UUID.randomUUID(), admittedAt, false, null, null, null);
    private final AdmissionReportFact close = new AdmissionReportFact(Kind.CLOSED, admission, patient, null, null,
            admittedAt.plusSeconds(3600), false, null, UUID.randomUUID(), null);

    @Test void closeBeforeStart_remainsPendingThenPairsWithoutReopening() {
        var pending = new AdmissionReportHistory(admission, null, null).apply(close);
        assertThat(pending.status()).isEqualTo(Status.PENDING_START);
        assertThat(pending.departmentId()).isNull();
        var paired = pending.apply(start);
        assertThat(paired.status()).isEqualTo(Status.CLOSED);
        assertThat(paired.departmentId()).isEqualTo(department);
        assertThat(paired.apply(start).apply(close)).isEqualTo(paired);
    }
    @Test void changedPatient_rejectsEvenOnExactAdmission() {
        var other = new AdmissionReportFact(Kind.CLOSED, admission, UUID.randomUUID(), null, null,
                close.businessAt(), false, null, close.settlementId(), null);
        assertThatThrownBy(() -> new AdmissionReportHistory(admission, start, null).apply(other)).hasMessageContaining("patient");
    }
    @Test void closeBeforeAdmittedAt_rejectsInEitherDeliveryOrder() {
        var early = new AdmissionReportFact(Kind.CLOSED, admission, patient, null, null,
                admittedAt.minusNanos(1), false, null, close.settlementId(), null);
        assertThatThrownBy(() -> new AdmissionReportHistory(admission, start, null).apply(early)).hasMessageContaining("chronological");
        assertThatThrownBy(() -> new AdmissionReportHistory(admission, null, early).apply(start)).hasMessageContaining("chronological");
    }
    @Test void changedDepartmentOrNano_requiresExplicitCorrectionContract() {
        var changed = new AdmissionReportFact(Kind.STARTED, admission, patient, UUID.randomUUID(), start.bedId(),
                admittedAt, false, null, null, null);
        var history = new AdmissionReportHistory(admission, start, null);
        assertThatThrownBy(() -> history.apply(changed)).hasMessageContaining("correction");
        var nanos = new AdmissionReportFact(Kind.STARTED, admission, patient, department, start.bedId(),
                admittedAt.plusNanos(1), false, null, null, null);
        assertThatThrownBy(() -> history.apply(nanos)).hasMessageContaining("correction");
    }
    @Test void closeMissingOrAmbiguousFinancialProof_rejects() {
        assertThatThrownBy(() -> new AdmissionReportFact(Kind.CLOSED, admission, patient, null, null,
                admittedAt, false, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AdmissionReportFact(Kind.CLOSED, admission, patient, null, null,
                admittedAt, false, null, UUID.randomUUID(), UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
    }
}
