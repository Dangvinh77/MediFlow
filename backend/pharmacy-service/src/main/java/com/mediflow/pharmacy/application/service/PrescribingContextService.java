package com.mediflow.pharmacy.application.service;

import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.dto.response.PrescribingContextDTO;
import com.mediflow.pharmacy.application.port.in.GetPrescribingContextUseCase;
import com.mediflow.pharmacy.application.port.out.OutpatientPrescriptionContextPort;
import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

public class PrescribingContextService implements GetPrescribingContextUseCase {
    private final OutpatientPrescriptionContextPort contexts;
    private final List<String> priceCodes;
    private final Clock clock;
    public PrescribingContextService(OutpatientPrescriptionContextPort contexts, List<String> priceCodes, Clock clock) {
        this.contexts = contexts; this.priceCodes = List.copyOf(priceCodes); this.clock = clock;
    }
    @Override
    public PrescribingContextDTO get(UUID recordId, ActorIdentity actor, String correlationId) {
        if (actor == null || !actor.isAdministrator() && (!"DOCTOR".equals(actor.role()) || actor.staffId() == null))
            throw new ForbiddenOperationException("PRESCRIPTION_CREATION_FORBIDDEN", "Danh tính bác sĩ chưa được xác nhận");
        var source = contexts.findRecord(recordId, correlationId);
        source.requireExact(recordId, source.patientId(), source.doctorId(), source.departmentId(), source.careEpisodeId(), clock.instant());
        if (!actor.isAdministrator() && !actor.staffId().equals(source.doctorId()))
            throw new ForbiddenOperationException("PRESCRIPTION_CREATION_FORBIDDEN", "Hồ sơ thuộc bác sĩ khác");
        if (priceCodes.isEmpty()) throw new PrescriptionRuleException("PHARMACY_PRICE_POLICY_UNAVAILABLE", "Chưa cấu hình mã thu tiền thuốc. Liên hệ quản trị viên.");
        return new PrescribingContextDTO(source.recordId(), source.patientId(), source.doctorId(), source.departmentId(),
                source.careEpisodeType(), source.careEpisodeId(), priceCodes);
    }
}
