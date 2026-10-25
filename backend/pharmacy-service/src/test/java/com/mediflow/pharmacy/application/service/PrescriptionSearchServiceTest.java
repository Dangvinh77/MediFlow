package com.mediflow.pharmacy.application.service;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.pharmacy.application.port.in.GetPrescriptionUseCase;
import com.mediflow.pharmacy.application.port.out.PrescriptionSearchPort;
import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;
import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrescriptionSearchServiceTest {
    private final PrescriptionSearchPort search = mock(PrescriptionSearchPort.class);
    private final GetPrescriptionUseCase reader = mock(GetPrescriptionUseCase.class);
    private final PrescriptionSearchService service = new PrescriptionSearchService(search, reader);

    @Test void rejectsInvalidStatusBeforeReadingData() {
        assertThatThrownBy(() -> service.search(null, null, "unknown", PageQuery.of(0, 20)))
                .isInstanceOf(PrescriptionRuleException.class);
        verifyNoInteractions(search, reader);
    }

    @Test void mapsTheRequestedPageThroughTheExistingPrescriptionReader() {
        UUID patient = UUID.randomUUID();
        var page = PageQuery.of(1, 20);
        when(search.search("drug", patient, PrescriptionStatus.ACTIVE, page))
                .thenReturn(PageResult.of(List.of(), 21, 1, 20));
        var result = service.search("drug", patient, "ACTIVE", page);
        assertThat(result.totalElements()).isEqualTo(21);
        assertThat(result.number()).isEqualTo(1);
        verify(search).search("drug", patient, PrescriptionStatus.ACTIVE, page);
        verifyNoInteractions(reader);
    }
}
