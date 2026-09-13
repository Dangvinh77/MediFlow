package com.mediflow.clinical.infrastructure.client;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.exception.UpstreamUnavailableException;
import com.mediflow.clinical.application.port.out.StaffLookupPort;
import com.mediflow.common.api.ApiResponse;

import feign.FeignException;

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
        } catch (FeignException.NotFound exception) {
            return Optional.empty();
        } catch (UpstreamUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new UpstreamUnavailableException("organization-service is unavailable", exception);
        }
    }
}
