package com.mediflow.pharmacy.infrastructure.client;

import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import org.springframework.cloud.openfeign.FallbackFactory;

public final class PharmacyBillingClearanceFallbackFactory implements FallbackFactory<PharmacyBillingClearanceFeignClient> {
    @Override public PharmacyBillingClearanceFeignClient create(Throwable cause) {
        return (id,token,correlation)->{throw new PharmacyUpstreamUnavailableException();};
    }
}
