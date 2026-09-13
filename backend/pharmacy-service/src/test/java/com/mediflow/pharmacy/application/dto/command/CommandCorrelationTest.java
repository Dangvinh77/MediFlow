package com.mediflow.pharmacy.application.dto.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.pharmacy.application.dto.request.CreatePrescriptionRequest;

/** Kiểm tra chuẩn hóa correlation ở boundary command trước khi chạy use case. */
class CommandCorrelationTest {

    /** Correlation trống phải được sinh tự động và correlation có dữ liệu phải được trim. */
    @Test
    void commands_normalizeCorrelation() {
        CreatePrescriptionRequest request = new CreatePrescriptionRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                java.time.LocalDate.now(), java.util.List.of());

        CreatePrescriptionCommand create = new CreatePrescriptionCommand(
                request, UUID.randomUUID(), false, "  request-1  ");
        CancelPrescriptionCommand cancel = new CancelPrescriptionCommand(
                UUID.randomUUID(), UUID.randomUUID(), false, "reason", " ");

        assertThat(create.correlationId()).isEqualTo("request-1");
        assertThat(cancel.correlationId()).isNotBlank();
        assertThat(UUID.fromString(cancel.correlationId())).isNotNull();
    }
}
