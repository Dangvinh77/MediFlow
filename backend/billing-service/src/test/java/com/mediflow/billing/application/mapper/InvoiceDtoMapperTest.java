package com.mediflow.billing.application.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.billing.application.dto.response.FeeDTO;
import com.mediflow.billing.application.dto.response.InvoiceDTO;
import com.mediflow.billing.domain.model.FeeType;
import com.mediflow.billing.domain.model.Invoice;
import com.mediflow.billing.domain.model.PaymentMethod;
import com.mediflow.billing.domain.model.SagaStatus;

class InvoiceDtoMapperTest {

    private final InvoiceDtoMapper mapper = new InvoiceDtoMapperImpl();

    @Test
    void toDto_mergesInvoiceScalarsAndFeeList() {
        UUID invoiceId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        Invoice invoice = Invoice.restore(invoiceId, patientId, LocalDate.of(2026, 9, 2),
                new BigDecimal("300000.00"), true, PaymentMethod.TRANSFER, null,
                UUID.randomUUID(), SagaStatus.AWAITING_DISPENSE,
                Instant.parse("2026-09-02T09:30:00Z"),
                Instant.parse("2026-09-02T09:00:00Z"), null);

        FeeDTO fee = new FeeDTO(UUID.randomUUID(), FeeType.DRUG, UUID.randomUUID(),
                LocalDate.of(2026, 9, 2), new BigDecimal("300000.00"), true);

        InvoiceDTO dto = mapper.toDto(invoice, List.of(fee));

        assertThat(dto.invoiceId()).isEqualTo(invoiceId);
        assertThat(dto.patientId()).isEqualTo(patientId);
        assertThat(dto.totalAmount()).isEqualByComparingTo("300000.00");
        assertThat(dto.isPaid()).isTrue();
        assertThat(dto.paymentMethod()).isEqualTo(PaymentMethod.TRANSFER);
        assertThat(dto.sagaStatus()).isEqualTo(SagaStatus.AWAITING_DISPENSE);
        assertThat(dto.fees()).containsExactly(fee);
    }

    @Test
    void toDto_normalInvoiceUnpaid_isPaidFalseAndSagaNone() {
        Invoice invoice = Invoice.restore(UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(), new BigDecimal("50000.00"), false, null, null, null,
                SagaStatus.NONE, null, Instant.now(), null);

        InvoiceDTO dto = mapper.toDto(invoice, List.of());

        assertThat(dto.isPaid()).isFalse();
        assertThat(dto.sagaStatus()).isEqualTo(SagaStatus.NONE);
        assertThat(dto.fees()).isEmpty();
    }
}
