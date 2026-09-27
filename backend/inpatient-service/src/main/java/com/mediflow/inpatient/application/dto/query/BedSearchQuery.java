package com.mediflow.inpatient.application.dto.query;

import com.mediflow.common.api.PageQuery;
import com.mediflow.inpatient.domain.model.enums.BedStatus;

import java.util.UUID;

public record BedSearchQuery(UUID maKhoa, String maKhu, BedStatus status, PageQuery phanTrang) {
}
