package com.mediflow.clinical.infrastructure.messaging;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.clinical.infrastructure.config.RabbitConfig;
import com.mediflow.clinical.infrastructure.persistence.jpaEntity.ClinicalOutboxEventJpaEntity;
import com.mediflow.clinical.infrastructure.persistence.repository.ClinicalOutboxJpaRepository;

@Component
@ConditionalOnProperty(prefix = "mediflow.features.care-finance-v2", name = "enabled", havingValue = "true")
public class ClinicalOutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(ClinicalOutboxDispatcher.class);

    private final ClinicalOutboxJpaRepository outbox;
    private final RabbitTemplate rabbitTemplate;

    public ClinicalOutboxDispatcher(ClinicalOutboxJpaRepository outbox, RabbitTemplate rabbitTemplate) {
        this.outbox = outbox;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${mediflow.clinical.outbox-poll-interval:1000}")
    @Transactional
    public void dispatch() {
        for (ClinicalOutboxEventJpaEntity event : outbox.lockUnpublished(100)) {
            try {
                rabbitTemplate.convertAndSend(RabbitConfig.EVENTS_EXCHANGE, event.getEventType(), event.getPayload());
                event.setPublishedAt(Instant.now());
            } catch (RuntimeException failure) {
                event.setRetryCount(event.getRetryCount() + 1);
                log.warn("Clinical outbox publish failed eventId={} eventType={} retryCount={}",
                        event.getEventId(), event.getEventType(), event.getRetryCount());
            }
            outbox.save(event);
        }
    }
}
