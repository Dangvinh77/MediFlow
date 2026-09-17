package com.mediflow.clinical.infrastructure.client;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.exception.UpstreamUnavailableException;
import com.mediflow.clinical.application.port.out.StaffLookupPort;
import com.mediflow.common.api.ApiResponse;

@Component
public class StaffLookupAdapter implements StaffLookupPort {

    private final OrganizationFeignClient client;

    public StaffLookupAdapter(OrganizationFeignClient client) {
        this.client = client;
    }

    @Override
    public Optional<UUID> departmentOf(UUID staffId) {
        try {
            ApiResponse<StaffExistsResponse> response = client.exists(staffId);
            if (response == null || !response.success() || response.data() == null) {
                throw new UpstreamUnavailableException("organization-service returned an invalid response");
            }
            StaffExistsResponse staff = response.data();
            if (!staff.exists()) {
                return Optional.empty();
            }
            if (staff.departmentId() == null) {
                throw new UpstreamUnavailableException("organization-service returned an invalid response");
            }
            return Optional.of(staff.departmentId());
        } catch (UpstreamUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (UpstreamExceptionClassifier.classify(exception)
                    == UpstreamExceptionClassifier.Classification.NOT_FOUND) {
                return Optional.empty();
            }
            throw new UpstreamUnavailableException("organization-service is unavailable", exception);
        }
    }
}
