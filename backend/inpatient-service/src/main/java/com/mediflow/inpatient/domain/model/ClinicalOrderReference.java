package com.mediflow.inpatient.domain.model;

import com.mediflow.inpatient.domain.exception.AdmissionRuleViolationException;
import com.mediflow.inpatient.domain.model.enums.ClinicalOrderType;
import com.mediflow.inpatient.domain.model.enums.ExternalOrderStatus;

import java.util.Objects;
import java.util.UUID;

public final class ClinicalOrderReference {

    private final UUID maThamChieu;
    private final UUID maDotNoiTru;
    private final ClinicalOrderType loaiYLenh;
    private final UUID maYLenhBenNgoai;
    private ExternalOrderStatus status;
    private String tomTat;
    private Integer phienBanSuKien;

    private ClinicalOrderReference(UUID id, UUID admissionId, ClinicalOrderType orderType,
                                   UUID externalOrderId, ExternalOrderStatus status,
                                   String summary, Integer eventVersion) {
        this.maThamChieu = Objects.requireNonNull(id);
        this.maDotNoiTru = Objects.requireNonNull(admissionId);
        this.loaiYLenh = Objects.requireNonNull(orderType);
        this.maYLenhBenNgoai = Objects.requireNonNull(externalOrderId);
        this.status = Objects.requireNonNull(status);
        this.tomTat = summary;
        this.phienBanSuKien = eventVersion;
    }

    public static ClinicalOrderReference create(UUID admissionId, ClinicalOrderType orderType,
                                                UUID externalOrderId, ExternalOrderStatus status,
                                                String summary, Integer eventVersion) {
        return new ClinicalOrderReference(UUID.randomUUID(), admissionId, orderType,
                externalOrderId, status, summary, eventVersion);
    }

    public static ClinicalOrderReference restore(UUID id, UUID admissionId, ClinicalOrderType orderType,
                                                 UUID externalOrderId, ExternalOrderStatus status,
                                                 String summary, Integer eventVersion) {
        return new ClinicalOrderReference(id, admissionId, orderType, externalOrderId,
                status, summary, eventVersion);
    }

    public boolean applyFact(UUID admissionId, UUID externalOrderId, ExternalOrderStatus nextStatus,
                             String nextSummary, Integer eventVersion) {
        if (!maDotNoiTru.equals(admissionId) || !maYLenhBenNgoai.equals(externalOrderId)) {
            throw new AdmissionRuleViolationException("INPATIENT_EXTERNAL_ORDER_MISMATCH",
                    "External fact must match the exact admission and order IDs");
        }
        if (eventVersion != null && phienBanSuKien != null && eventVersion <= phienBanSuKien) {
            return false;
        }
        if (isTerminal(status)) {
            return false;
        }
        this.status = Objects.requireNonNull(nextStatus);
        this.tomTat = nextSummary;
        this.phienBanSuKien = eventVersion;
        return true;
    }

    private static boolean isTerminal(ExternalOrderStatus value) {
        return value == ExternalOrderStatus.COMPLETED
                || value == ExternalOrderStatus.CANCELLED
                || value == ExternalOrderStatus.FAILED;
    }

    public UUID referenceId() { return maThamChieu; }
    public UUID admissionId() { return maDotNoiTru; }
    public ClinicalOrderType orderType() { return loaiYLenh; }
    public UUID externalOrderId() { return maYLenhBenNgoai; }
    public ExternalOrderStatus status() { return status; }
    public String summary() { return tomTat; }
    public Integer eventVersion() { return phienBanSuKien; }
}
