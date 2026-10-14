package com.mediflow.core.config;

import com.mediflow.patient.infrastructure.config.RabbitConfig;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CoreRabbitConfiguration {

    @Bean
    TopicExchange mediflowEventsExchange() {
        return new TopicExchange(RabbitConfig.EXCHANGE, true, false);
    }

    @Bean
    TopicExchange mediflowEventsDeadLetterExchange() {
        return new TopicExchange(RabbitConfig.DLX, true, false);
    }

    @Bean
    Queue patientDeadLetterQueue() {
        return QueueBuilder.durable(RabbitConfig.DLQ).build();
    }

    @Bean
    Queue patientQueue() {
        return QueueBuilder.durable(RabbitConfig.QUEUE)
                .deadLetterExchange(RabbitConfig.DLX)
                .deadLetterRoutingKey("patient.dead-letter")
                .build();
    }

    @Bean
    Declarables patientQueueBindings(Queue patientQueue, TopicExchange mediflowEventsExchange) {
        return new Declarables(BindingBuilder.bind(patientQueue)
                .to(mediflowEventsExchange).with(RabbitConfig.RK_PAYMENT_COMPLETED));
    }

    @Bean
    Binding patientDeadLetterBinding(Queue patientDeadLetterQueue,
                                     TopicExchange mediflowEventsDeadLetterExchange) {
        return BindingBuilder.bind(patientDeadLetterQueue)
                .to(mediflowEventsDeadLetterExchange).with("patient.dead-letter");
    }

    @Bean
    MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
