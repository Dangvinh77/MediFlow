package com.mediflow.inpatient.infrastructure.messaging;

import com.mediflow.inpatient.application.dto.event.AdmissionClosedEvent;
import com.mediflow.inpatient.application.dto.event.AdmissionDepositRequestedEvent;
import com.mediflow.inpatient.application.dto.event.AdmissionStartedEvent;
import com.mediflow.inpatient.application.dto.event.DomainEventEnvelope;
import com.mediflow.inpatient.application.dto.event.MedicalDischargeApprovedEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Maps application event records to the canonical shared event wire names. */
@Component
public class InpatientEventWireMapper {

    public Map<String, Object> toWireEnvelope(DomainEventEnvelope<?> event) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", event.maSuKien());
        envelope.put("eventType", event.loaiSuKien());
        envelope.put("version", event.phienBan());
        envelope.put("occurredAt", event.xayRaLuc());
        envelope.put("correlationId", event.maTuongQuan());
        envelope.put("producer", event.dichVuPhat());
        envelope.put("payload", toWirePayload(event.duLieu()));
        return envelope;
    }

    private static Map<String, Object> toWirePayload(Object payload) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (payload instanceof AdmissionDepositRequestedEvent event) {
            body.put("admissionId", event.maDotNoiTru());
            body.put("patientId", event.maBenhNhan());
            body.put("departmentId", event.maKhoa());
            body.put("careEpisodeType", event.loaiTapNoiTru());
            body.put("careEpisodeId", event.maTapNoiTru());
            body.put("sourceType", event.loaiNguon());
            body.put("sourceId", event.maNguon());
            body.put("priceCode", event.maBangGia());
            body.put("suggestedAmount", event.soTienGoiY());
            body.put("reason", event.lyDo());
        } else if (payload instanceof AdmissionStartedEvent event) {
            body.put("admissionId", event.maDotNoiTru());
            body.put("patientId", event.maBenhNhan());
            body.put("bedId", event.maGiuong());
            body.put("departmentId", event.maKhoa());
            body.put("admittedAt", event.thoiGianNhapVien());
            body.put("emergency", event.capCuu());
            body.put("emergencyOverrideId", event.maPheDuyetNgoaiLe());
        } else if (payload instanceof MedicalDischargeApprovedEvent event) {
            body.put("admissionId", event.maDotNoiTru());
            body.put("patientId", event.maBenhNhan());
            body.put("summaryId", event.maTomTat());
            body.put("approvedBy", event.nguoiDuyet());
            body.put("approvedAt", event.thoiGianDuyet());
        } else if (payload instanceof AdmissionClosedEvent event) {
            body.put("admissionId", event.maDotNoiTru());
            body.put("patientId", event.maBenhNhan());
            body.put("settlementId", event.maQuyetToan());
            body.put("approvedOverrideId", event.maPheDuyetDong());
            body.put("closedAt", event.thoiGianDong());
        } else {
            throw new IllegalArgumentException("Unsupported inpatient event payload: "
                    + (payload == null ? "null" : payload.getClass().getName()));
        }
        return body;
    }
}
