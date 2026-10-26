package com.mediflow.clinical.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.mediflow.clinical.domain.model.FollowUpRecommendation;

public interface FollowUpRecommendationRepositoryPort {
    FollowUpRecommendation save(FollowUpRecommendation recommendation, UUID eventId, String correlationId);

    Optional<FollowUpRecommendation> findBySourceRecordId(UUID sourceRecordId);
}
