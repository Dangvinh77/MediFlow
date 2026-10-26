package com.mediflow.clinical.application.port.out;

import com.mediflow.clinical.application.event.DomainEventEnvelope;
import com.mediflow.clinical.application.event.FollowUpRecommendationCreatedFact;

public interface FollowUpRecommendationOutboxPort {
    void append(DomainEventEnvelope<FollowUpRecommendationCreatedFact> event);
}
