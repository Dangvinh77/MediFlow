package com.mediflow.pharmacy.application.port.out;
/**
 * 
 * ProcessedEventPort
 * Vì sao cần: RabbitMQ có thể gửi lại cùng một event. Nếu không kiểm tra,
 *  một tin payment.completed đến hai lần sẽ xuất thuốc hai lần.
 */

import java.util.UUID;

import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.ProcessedEventJpaEntity;

public interface ProcessedEventPort {
    //kiểm tra event đã được xử lí chưa
    ProcessedEventJpaEntity alreadyProcessed(UUID eventId);

    //Đánh dấu event đã được xử lí
    ProcessedEventJpaEntity markProcessed(UUID eventId, String routingKey);

}
