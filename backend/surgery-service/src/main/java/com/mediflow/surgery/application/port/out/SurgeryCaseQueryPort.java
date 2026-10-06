package com.mediflow.surgery.application.port.out;

import com.mediflow.common.api.PageResult;
import com.mediflow.surgery.application.dto.response.SurgeryCaseBoardItem;
import com.mediflow.surgery.application.port.in.QuerySurgeryCasesUseCase;

/** A board query is a projection, not a paginated collection of full aggregates. */
public interface SurgeryCaseQueryPort {
    PageResult<SurgeryCaseBoardItem> search(QuerySurgeryCasesUseCase.Filter filter);
}
