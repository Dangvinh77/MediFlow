package com.mediflow.clinical.application.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mediflow.clinical.application.dto.authority.LabOrderLifecycleSnapshot;
import com.mediflow.clinical.application.dto.response.RecordLabOrderDTO;
import com.mediflow.clinical.application.port.out.ClinicalLabOrderRepositoryPort;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.LabOrderLifecyclePort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.domain.exception.ClinicalRecordNotFoundException;
import com.mediflow.clinical.domain.model.ClinicalLabOrder;
import com.mediflow.clinical.domain.model.MedicalRecord;

/** Shows a doctor which orders still block ending a visit, using Lab's own current status. */
@Service
public class RecordLabOrderApplicationService {
    private static final String NOT_RECEIVED = "NOT_RECEIVED";

    private final MedicalRecordRepositoryPort records;
    private final ClinicalLabOrderRepositoryPort orders;
    private final LabOrderLifecyclePort lifecycle;
    private final RecordReadGuard readGuard;
    private final CorrelationIdProvider correlationIds;

    public RecordLabOrderApplicationService(MedicalRecordRepositoryPort records, ClinicalLabOrderRepositoryPort orders,
            LabOrderLifecyclePort lifecycle, RecordReadGuard readGuard, CorrelationIdProvider correlationIds) {
        this.records = records;
        this.orders = orders;
        this.lifecycle = lifecycle;
        this.readGuard = readGuard;
        this.correlationIds = correlationIds;
    }

    public List<RecordLabOrderDTO> list(UUID recordId) {
        MedicalRecord record = records.findById(recordId)
                .orElseThrow(() -> new ClinicalRecordNotFoundException("Medical record not found: " + recordId));
        readGuard.requireRecord(record);
        String correlationId = correlationIds.currentOrCreate().toString();
        return orders.findByRecordId(recordId).stream().map(order -> live(order, correlationId)).toList();
    }

    private RecordLabOrderDTO live(ClinicalLabOrder order, String correlationId) {
        LabOrderLifecycleSnapshot current = lifecycle.lifecycle(order.sourceOrderId(), correlationId);
        ClinicalLabCompletionGuard.requireWellFormed(order, current);
        if (!current.exists()) {
            return new RecordLabOrderDTO(order.sourceOrderId(), null, order.labType(), NOT_RECEIVED, order.orderedAt());
        }
        ClinicalLabCompletionGuard.requireSameBinding(order, current);
        return new RecordLabOrderDTO(order.sourceOrderId(), current.testId(), order.labType(), current.status(), order.orderedAt());
    }
}
