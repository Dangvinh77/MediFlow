package com.mediflow.inpatient.infrastructure.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.inpatient.application.dto.command.SettlementCompletedCommand;
import com.mediflow.inpatient.application.port.in.ReactToAdmissionReferralUseCase;
import com.mediflow.inpatient.application.port.in.ReactToDepositTopupUseCase;
import com.mediflow.inpatient.application.port.in.ReactToExternalOrderUseCase;
import com.mediflow.inpatient.application.port.in.ReactToFinancialClearanceUseCase;
import com.mediflow.inpatient.application.port.in.ReactToSettlementUseCase;
import com.mediflow.inpatient.domain.model.enums.SettlementOutcome;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class SettlementCompletedContractFixtureTest {

    private static final String FIXTURE = "/contracts/settlement.completed.v1.json";

    private final ReactToAdmissionReferralUseCase referrals = mock(ReactToAdmissionReferralUseCase.class);
    private final ReactToFinancialClearanceUseCase clearances = mock(ReactToFinancialClearanceUseCase.class);
    private final ReactToSettlementUseCase settlements = mock(ReactToSettlementUseCase.class);
    private final ReactToDepositTopupUseCase topups = mock(ReactToDepositTopupUseCase.class);
    private final ReactToExternalOrderUseCase externalOrders = mock(ReactToExternalOrderUseCase.class);
    private final InpatientEventConsumer consumer = new InpatientEventConsumer(
            new ObjectMapper(), referrals, clearances, settlements, topups, externalOrders);

    @Test
    void billingFixtureMapsEverySettlementFieldToApplicationCommand() throws IOException {
        consumer.receive(new Message(readFixture(), new MessageProperties()));

        ArgumentCaptor<SettlementCompletedCommand> command =
                ArgumentCaptor.forClass(SettlementCompletedCommand.class);
        verify(settlements).onSettlementCompleted(command.capture());

        SettlementCompletedCommand actual = command.getValue();
        assertThat(actual.maSuKien()).isEqualTo(id(99));
        assertThat(actual.phienBan()).isEqualTo(1);
        assertThat(actual.xayRaLuc()).isEqualTo(Instant.parse("2026-10-10T08:00:00Z"));
        assertThat(actual.maTuongQuan()).isEqualTo(id(9).toString());
        assertThat(actual.maQuyetToan()).isEqualTo(id(98));
        assertThat(actual.maDotNoiTru()).isEqualTo(id(3));
        assertThat(actual.maTaiKhoan()).isEqualTo(id(1));
        assertThat(actual.tongTien()).isEqualByComparingTo(new BigDecimal("500000.00"));
        assertThat(actual.baoHiemThanhToan()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(actual.benhNhanPhaiTra()).isEqualByComparingTo(new BigDecimal("500000.00"));
        assertThat(actual.daThanhToan()).isEqualByComparingTo(new BigDecimal("500000.00"));
        assertThat(actual.daHoanTien()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(actual.soDu()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(actual.ketQua()).isEqualTo(SettlementOutcome.PAID_IN_FULL);
        assertThat(actual.hoanTatLuc()).isEqualTo(Instant.parse("2026-10-10T08:00:00Z"));
        verifyNoInteractions(referrals, clearances, topups, externalOrders);
    }

    private byte[] readFixture() throws IOException {
        try (var input = getClass().getResourceAsStream(FIXTURE)) {
            assertThat(input).as("canonical Billing settlement.completed fixture").isNotNull();
            return input.readAllBytes();
        }
    }

    private static UUID id(int suffix) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(suffix));
    }
}
