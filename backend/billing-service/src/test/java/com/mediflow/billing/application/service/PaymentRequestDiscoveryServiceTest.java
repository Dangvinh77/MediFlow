package com.mediflow.billing.application.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.billing.application.dto.command.PaymentRequestSourcePurpose;
import com.mediflow.billing.application.dto.command.PaymentRequestSourceQuery;
import com.mediflow.billing.application.dto.response.PaymentRequestDiscoveryDTO;
import com.mediflow.billing.application.port.out.PaymentRequestDiscoveryPort;
import com.mediflow.billing.domain.exception.LedgerPaymentRequestNotFoundException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentRequestDiscoveryServiceTest {
    private final PaymentRequestDiscoveryPort port = mock(PaymentRequestDiscoveryPort.class);
    private final PaymentRequestDiscoveryService service = new PaymentRequestDiscoveryService(port);

    private static PaymentRequestSourceQuery query() {
        return new PaymentRequestSourceQuery(PaymentRequestSourcePurpose.EXAM, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void discover_portFindsExactMatch_returnsItUnchanged() {
        var query = query();
        var dto = new PaymentRequestDiscoveryDTO(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                query.patientId(), UUID.randomUUID(), "EXAM", "EXAM", query.sourceId(), query.careEpisodeId(),
                "OUTPATIENT_VISIT", query.sourceId(), null, null, List.of(), new BigDecimal("1.00"),
                BigDecimal.ZERO, new BigDecimal("1.00"), "VND", "PENDING", "OPEN", null, Instant.now());
        when(port.findExact(query)).thenReturn(Optional.of(dto));
        assertThat(service.discover(query)).isSameAs(dto);
    }

    @Test
    void discover_portFindsNothing_throwsNotFoundWithoutCallingAnythingElse() {
        var query = query();
        when(port.findExact(query)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.discover(query))
                .isInstanceOf(LedgerPaymentRequestNotFoundException.class)
                .hasMessageContaining(query.sourceId().toString());
        verify(port).findExact(query);
        verifyNoMoreInteractions(port);
    }
}
