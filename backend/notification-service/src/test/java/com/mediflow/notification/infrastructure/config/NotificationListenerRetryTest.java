package com.mediflow.notification.infrastructure.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.util.concurrent.atomic.AtomicInteger;
import com.rabbitmq.client.Channel;
import org.aopalliance.aop.Advice;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationListenerRetryTest {
    @Test void temporaryStorageFailureRecoversWithinThreeAttempts() {
        var attempts = new AtomicInteger();
        Listener listener = advised((channel, message) -> {
            if (attempts.incrementAndGet() < 3) throw new TransientDataAccessResourceException("temporary outage");
        });
        listener.receive(null, message());
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test void exhaustedStorageFailureRejectsAfterThreeAttemptsInsteadOfRequeueingForever() {
        var attempts = new AtomicInteger();
        Listener listener = advised((channel, message) -> {
            attempts.incrementAndGet();
            throw new TransientDataAccessResourceException("still unavailable");
        });
        assertThatThrownBy(() -> listener.receive(null, message())).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test void wrappedPermanentContractFailureIsRejectedOnFirstAttempt() {
        var attempts = new AtomicInteger();
        Listener listener = advised((channel, message) -> {
            attempts.incrementAndGet();
            throw new ListenerExecutionFailedException("listener rejected wire contract",
                    new AmqpRejectAndDontRequeueException("invalid producer"), message);
        });
        assertThatThrownBy(() -> listener.receive(null, message())).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThat(attempts.get()).isOne();
    }

    private Listener advised(Listener target) {
        var factory = new RabbitConfig().notificationListenerFactory(mock(ConnectionFactory.class), false);
        var proxy = new ProxyFactory(target);
        // Exercise the interceptor wired to the production container, including its AMQP recoverer.
        var advice = (Advice[]) ReflectionTestUtils.getField(factory, "adviceChain");
        assertThat(advice).isNotEmpty();
        for (var item : advice) proxy.addAdvice(item);
        return (Listener) proxy.getProxy();
    }

    private Message message() { return new Message(new byte[]{1}, new MessageProperties()); }
    public interface Listener { void receive(Channel channel, Message message); }
}
