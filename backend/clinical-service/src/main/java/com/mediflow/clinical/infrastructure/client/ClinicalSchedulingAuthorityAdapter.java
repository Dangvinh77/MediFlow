package com.mediflow.clinical.infrastructure.client;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.dto.authority.ConsultationEligibilitySnapshot;
import com.mediflow.clinical.application.dto.authority.CurrentDoctorAuthoritySnapshot;
import com.mediflow.clinical.application.dto.authority.SchedulingAuthoritySnapshot;
import com.mediflow.clinical.application.exception.UpstreamUnavailableException;
import com.mediflow.clinical.application.port.out.SchedulingAuthorityPort;
import com.mediflow.common.api.ApiResponse;

@Component
public class ClinicalSchedulingAuthorityAdapter implements SchedulingAuthorityPort {
    private final ClinicalSchedulingAuthorityFeignClient client;

    public ClinicalSchedulingAuthorityAdapter(ClinicalSchedulingAuthorityFeignClient client) {
        this.client = client;
    }

    @Override
    public SchedulingAuthoritySnapshot authority(UUID actorAccountId, UUID departmentId, String correlationId) {
        if (actorAccountId == null || departmentId == null || correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("Verified actor, department and correlation are required");
        }
        try {
            ApiResponse<SchedulingAuthoritySnapshot> response = client.authority(actorAccountId, departmentId, correlationId);
            SchedulingAuthoritySnapshot data = requireData(response, correlationId);
            if (!actorAccountId.equals(data.actorAccountId()) || !departmentId.equals(data.departmentId())
                    || data.accountExists() == null
                    || data.accountActive() == null || data.staffActive() == null || data.departmentActive() == null
                    || data.canManageSchedules() == null || data.scopeRevision() == null || data.observedAt() == null
                    || data.scopeRevision() < 0) {
                throw unavailable("Core returned an invalid scheduling authority snapshot");
            }
            return data;
        } catch (UpstreamUnavailableException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw unavailable("Core scheduling authority is unavailable", failure);
        }
    }

    @Override
    public ConsultationEligibilitySnapshot consultationEligibility(
            UUID doctorId, UUID departmentId, UUID roomId, String correlationId) {
        if (doctorId == null || departmentId == null || roomId == null
                || correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("Requested consultation identities and correlation are required");
        }
        try {
            ApiResponse<ConsultationEligibilitySnapshot> response = client.consultationEligibility(
                    doctorId, departmentId, roomId, correlationId);
            ConsultationEligibilitySnapshot data = requireData(response, correlationId);
            if (!doctorId.equals(data.doctorId()) || !departmentId.equals(data.departmentId())
                    || !roomId.equals(data.roomId()) || data.doctorExists() == null || data.doctorActive() == null
                    || data.eligibleDoctor() == null || data.departmentExists() == null || data.departmentActive() == null
                    || data.roomExists() == null || data.roomActive() == null || data.eligible() == null
                    || data.observedAt() == null) {
                throw unavailable("Core returned an invalid consultation eligibility snapshot");
            }
            return data;
        } catch (UpstreamUnavailableException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw unavailable("Core consultation eligibility is unavailable", failure);
        }
    }

    @Override
    public CurrentDoctorAuthoritySnapshot currentDoctorAuthority(UUID actorAccountId, String correlationId) {
        if (actorAccountId == null || correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("Verified doctor actor and correlation are required");
        }
        try {
            ApiResponse<CurrentDoctorAuthoritySnapshot> response = client.currentDoctorAuthority(actorAccountId, correlationId);
            CurrentDoctorAuthoritySnapshot data = requireData(response, correlationId);
            if (!actorAccountId.equals(data.actorAccountId()) || data.accountExists() == null
                    || data.accountActive() == null || data.staffActive() == null
                    || data.departmentActive() == null || data.observedAt() == null) {
                throw unavailable("Core returned an invalid current-doctor authority snapshot");
            }
            return data;
        } catch (UpstreamUnavailableException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw unavailable("Core current-doctor authority is unavailable", failure);
        }
    }

    private static <T> T requireData(ApiResponse<T> response, String expectedCorrelationId) {
        if (response == null || !response.success() || response.error() != null || response.data() == null
                || !expectedCorrelationId.equals(response.correlationId())) {
            throw unavailable("Core returned an invalid scheduling response envelope");
        }
        return response.data();
    }

    private static UpstreamUnavailableException unavailable(String message) {
        return new UpstreamUnavailableException("SCHEDULING_AUTHORITY_UNAVAILABLE", message);
    }

    private static UpstreamUnavailableException unavailable(String message, Throwable cause) {
        return new UpstreamUnavailableException("SCHEDULING_AUTHORITY_UNAVAILABLE", message, cause);
    }
}
