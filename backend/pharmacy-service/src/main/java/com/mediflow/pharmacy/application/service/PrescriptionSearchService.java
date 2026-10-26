package com.mediflow.pharmacy.application.service;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;
import com.mediflow.pharmacy.application.port.in.GetPrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.SearchPrescriptionsUseCase;
import com.mediflow.pharmacy.application.port.out.PrescriptionSearchPort;
import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;
import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrescriptionSearchService implements SearchPrescriptionsUseCase {
    private final PrescriptionSearchPort search;
    private final GetPrescriptionUseCase reader;
    public PrescriptionSearchService(PrescriptionSearchPort search, GetPrescriptionUseCase reader) {
        this.search = search; this.reader = reader;
    }
    @Override @Transactional(readOnly = true)
    public PageResult<PrescriptionDTO> search(String keyword, UUID patientId, String status, PageQuery page) {
        PrescriptionStatus filter = null;
        if (status != null && !status.isBlank()) {
            try { filter = PrescriptionStatus.valueOf(status.trim()); }
            catch (IllegalArgumentException invalid) {
                throw new PrescriptionRuleException("PRESCRIPTION_STATUS_INVALID", "Trạng thái đơn thuốc không hợp lệ");
            }
        }
        return search.search(keyword, patientId, filter, PageQuery.of(page.page(), page.size()))
                .map(reader::getPrescriptionById);
    }
}
