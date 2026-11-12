package com.mediflow.clinical.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.common.exception.ForbiddenOperationException;

class RecordHandoverTest {
    static final Instant AT = Instant.parse("2026-11-10T03:00:00Z");
    final UUID record = UUID.randomUUID();
    final UUID from = UUID.randomUUID();
    final UUID to = UUID.randomUUID();

    @Test void reasonAndPendingItemsRequired() {
        assertThatThrownBy(() -> RecordHandover.request(record, from, to, " ", "Chờ kết quả", AT))
                .extracting("code").isEqualTo("HANDOVER_INVALID");
        assertThatThrownBy(() -> RecordHandover.request(record, from, to, "Hết ca", "", AT))
                .extracting("code").isEqualTo("HANDOVER_INVALID");
        assertThatThrownBy(() -> RecordHandover.request(record, from, to, "x".repeat(501), "Chờ", AT))
                .extracting("code").isEqualTo("HANDOVER_INVALID");
        assertThatThrownBy(() -> RecordHandover.request(record, from, to, "Hết ca", "x".repeat(2001), AT))
                .extracting("code").isEqualTo("HANDOVER_INVALID");
    }

    @Test void selfHandoverIsRefused() {
        assertThatThrownBy(() -> RecordHandover.request(record, from, from, "Hết ca", "Chờ", AT))
                .extracting("code").isEqualTo("HANDOVER_INVALID");
    }

    @Test void onlyReceiverAccepts() {
        RecordHandover handover = RecordHandover.request(record, from, to, "Hết ca", "Chờ kết quả", AT);
        assertThatThrownBy(() -> handover.accept(from, AT)).isInstanceOf(ForbiddenOperationException.class);
        handover.accept(to, AT);
        assertThat(handover.getStatus()).isEqualTo(RecordHandoverStatus.ACCEPTED);
        assertThat(handover.getAcceptedAt()).isEqualTo(AT);
        assertThatThrownBy(() -> handover.accept(to, AT)).extracting("code").isEqualTo("HANDOVER_INVALID");
    }

    @Test void acceptingMakesReceiverResponsible() {
        MedicalRecord open = MedicalRecord.openForAppointment(UUID.randomUUID(), from, UUID.randomUUID(),
                LocalDate.parse("2026-11-10"), "Sốt", UUID.randomUUID(), AT);
        assertThat(open.responsibleDoctorId()).isEqualTo(from);
        open.handOverTo(to, AT);
        assertThat(open.responsibleDoctorId()).isEqualTo(to);
        assertThat(open.getDoctorId()).isEqualTo(from);
    }

    @Test void closedRecordCannotBeHandedOver() {
        MedicalRecord closed = MedicalRecord.openForAppointment(UUID.randomUUID(), from, UUID.randomUUID(),
                LocalDate.parse("2026-11-10"), "Sốt", UUID.randomUUID(), AT);
        closed.closeWithoutConclusion(RecordClosureReason.PATIENT_LEFT, "Tự về", from, AT);
        assertThatThrownBy(() -> closed.handOverTo(to, AT)).extracting("code").isEqualTo("CLINICAL_INVALID_STATUS_TRANSITION");
    }
}
