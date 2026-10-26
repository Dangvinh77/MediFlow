package com.mediflow.clinical.infrastructure.client;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.dto.authority.LabOrderLifecycleSnapshot;
import com.mediflow.clinical.application.exception.UpstreamUnavailableException;
import com.mediflow.clinical.application.port.out.LabOrderLifecyclePort;
import com.mediflow.common.api.ApiResponse;

@Component
public class ClinicalLabLifecycleAdapter implements LabOrderLifecyclePort {
    private static final Set<String> STATUSES = Set.of(
            "PENDING", "AWAITING_PAYMENT", "READY", "IN_PROGRESS", "COMPLETED", "CANCELLED");

    private final ClinicalLabLifecycleFeignClient client;

    public ClinicalLabLifecycleAdapter(ClinicalLabLifecycleFeignClient client) {
        this.client = client;
    }

    @Override
    public LabOrderLifecycleSnapshot lifecycle(UUID sourceOrderId, String correlationId) {
        if (sourceOrderId == null || correlationId == null || correlationId.isBlank()
                || correlationId.length() > 120) {
            throw new IllegalArgumentException("Source order and correlation are required");
        }
        try {
            ApiResponse<LabOrderLifecycleSnapshot> response = client.lifecycle(sourceOrderId, correlationId);
            if (response == null || !response.success() || response.error() != null || response.data() == null
                    || !correlationId.equals(response.correlationId())) {
                throw unavailable("Lab returned an invalid lifecycle response envelope");
            }
            LabOrderLifecycleSnapshot data = response.data();
            if (!sourceOrderId.equals(data.sourceOrderId()) || data.exists() == null || data.observedAt() == null) {
                throw unavailable("Lab returned an invalid lifecycle identity");
            }
            if (data.exists()) {
                if (data.testId() == null || data.recordId() == null || data.patientId() == null
                        || data.departmentId() == null || data.careEpisodeType() == null
                        || data.careEpisodeId() == null || !STATUSES.contains(data.status())) {
                    throw unavailable("Lab returned an incomplete lifecycle binding");
                }
            } else if (data.testId() != null || data.recordId() != null || data.patientId() != null
                    || data.departmentId() != null || data.careEpisodeType() != null
                    || data.careEpisodeId() != null || data.status() != null) {
                throw unavailable("Lab returned a malformed missing-order lifecycle");
            }
            return data;
        } catch (UpstreamUnavailableException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new UpstreamUnavailableException("LAB_LIFECYCLE_UNAVAILABLE",
                    "Lab source-order lifecycle is unavailable", failure);
        }
    }

    private static UpstreamUnavailableException unavailable(String message) {
        return new UpstreamUnavailableException("LAB_LIFECYCLE_UNAVAILABLE", message);
    }
}
