package com.mediflow.pharmacy.infrastructure.client;

import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import java.util.UUID;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.http.ResponseEntity;

public final class PharmacyOrganizationFallbackFactory implements FallbackFactory<PharmacyOrganizationFeignClient> {
    @Override public PharmacyOrganizationFeignClient create(Throwable cause) {
        return new PharmacyOrganizationFeignClient() {
            @Override public ResponseEntity<String> staff(UUID id, String authorization, String correlation) {
                throw new PharmacyUpstreamUnavailableException();
            }
            @Override public ResponseEntity<String> department(UUID id, String authorization, String correlation) {
                throw new PharmacyUpstreamUnavailableException();
            }
        };
    }
}
