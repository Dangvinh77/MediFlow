package com.mediflow.billing.application.port.in;

import com.mediflow.billing.application.dto.response.RegistrationSettlementDTO;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;

public interface ListRegistrationSettlementsUseCase {
    PageResult<RegistrationSettlementDTO> list(String kind, PageQuery page);
}
