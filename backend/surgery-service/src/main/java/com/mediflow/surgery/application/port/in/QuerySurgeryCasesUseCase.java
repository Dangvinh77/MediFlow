package com.mediflow.surgery.application.port.in;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.surgery.application.dto.response.SurgeryCaseBoardItem;
import com.mediflow.surgery.application.dto.response.SurgeryCaseDetails;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import java.time.Instant;
import java.util.UUID;

public interface QuerySurgeryCasesUseCase {
    SurgeryCaseDetails detail(UUID caseId, Viewer viewer, String correlationId);
    PageResult<SurgeryCaseBoardItem> list(Filter filter, Viewer viewer, String correlationId);

    enum ReadRole { ADMIN, MANAGER, DOCTOR, NURSE }
    /** Role and identities must be constructed from verified authentication, never HTTP parameters. */
    record Viewer(UUID accountId, UUID staffId, ReadRole role) {
        public Viewer {
            if (accountId == null || role == null) throw new IllegalArgumentException("Verified reader required");
        }
    }
    record Filter(UUID departmentId, SurgeryStatus status, Instant requestedFrom,
            Instant requestedUntil, Instant scheduledFrom, Instant scheduledUntil, PageQuery page) {
        public static Filter fromRaw(UUID departmentId, String status, Instant requestedFrom,
                Instant requestedUntil, Instant scheduledFrom, Instant scheduledUntil, PageQuery page) {
            return new Filter(departmentId,status == null ? null : SurgeryStatus.valueOf(status),
                    requestedFrom,requestedUntil,scheduledFrom,scheduledUntil,page);
        }
        public Filter {
            if (page == null) throw new IllegalArgumentException("Page required");
            page = PageQuery.of(page.page(), page.size());
            validInterval(requestedFrom, requestedUntil);
            validInterval(scheduledFrom, scheduledUntil);
        }
        private static void validInterval(Instant from, Instant until) {
            if (from != null && until != null && !until.isAfter(from)) {
                throw new com.mediflow.surgery.domain.exception.SurgeryRuleException(
                        "SURGERY_QUERY_INTERVAL_INVALID", "Query end must be after start");
            }
        }
        public Filter inDepartment(UUID department) {
            return new Filter(department, status, requestedFrom, requestedUntil,
                    scheduledFrom, scheduledUntil, page);
        }
    }
}
