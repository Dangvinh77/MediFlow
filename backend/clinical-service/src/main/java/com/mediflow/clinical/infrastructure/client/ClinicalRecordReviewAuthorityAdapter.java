package com.mediflow.clinical.infrastructure.client;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.exception.UpstreamUnavailableException;
import com.mediflow.clinical.application.port.out.RecordReviewAuthorityPort;
import com.mediflow.common.api.ApiResponse;

@Component
public class ClinicalRecordReviewAuthorityAdapter implements RecordReviewAuthorityPort {
    private final ClinicalRecordReviewAuthorityFeignClient client;

    public ClinicalRecordReviewAuthorityAdapter(ClinicalRecordReviewAuthorityFeignClient client) {
        this.client = client;
    }

    @Override
    public boolean canReviewRecords(UUID accountId, UUID correlationId) {
        if (accountId == null || correlationId == null) {
            throw new IllegalArgumentException("Verified account and correlation are required");
        }
        try {
            ApiResponse<ClinicalRecordReviewAuthorityFeignClient.RecordReviewAuthority> response =
                    client.authority(accountId, correlationId.toString());
            ClinicalRecordReviewAuthorityFeignClient.RecordReviewAuthority data =
                    requireData(response, correlationId.toString());
            if (!accountId.equals(data.actorAccountId()) || data.canReviewRecords() == null
                    || data.canReviewRecords() && data.staffId() == null) {
                throw unavailable("Core returned an invalid record review authority response");
            }
            return data.canReviewRecords();
        } catch (UpstreamUnavailableException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw unavailable("Core record review authority is unavailable", failure);
        }
    }

    private static ClinicalRecordReviewAuthorityFeignClient.RecordReviewAuthority requireData(
            ApiResponse<ClinicalRecordReviewAuthorityFeignClient.RecordReviewAuthority> response,
            String expectedCorrelationId) {
        if (response == null || !response.success() || response.error() != null || response.data() == null
                || !expectedCorrelationId.equals(response.correlationId())) {
            throw unavailable("Core returned an invalid record review authority envelope");
        }
        return response.data();
    }

    private static UpstreamUnavailableException unavailable(String message) {
        return new UpstreamUnavailableException("RECORD_REVIEW_AUTHORITY_UNAVAILABLE", message);
    }

    private static UpstreamUnavailableException unavailable(String message, Throwable cause) {
        return new UpstreamUnavailableException("RECORD_REVIEW_AUTHORITY_UNAVAILABLE", message, cause);
    }
}
