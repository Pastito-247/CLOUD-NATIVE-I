package com.ecommerce.users.messaging;

import com.ecommerce.users.config.MessagingProperties;
import com.ecommerce.users.event.UserRegisteredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifica que el productor publique user.registered en el exchange fanout de
 * usuarios (nombre centralizado en application.yml).
 */
class UserEventPublisherTest {

    private static final String USER_EXCHANGE = "user.exchange";

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

    private UserEventPublisher publisher;

    @BeforeEach
    void setUp() {
        MessagingProperties props = new MessagingProperties();
        props.getExchange().setUser(USER_EXCHANGE);
        publisher = new UserEventPublisher(rabbitTemplate, props);
    }

    @Test
    void publicaUsuarioRegistrado() {
        publisher.publishUserRegistered(
                new UserRegisteredEvent(1L, "cliente@tuki.cl", "Cliente", "customer", LocalDateTime.now()));

        verify(rabbitTemplate).convertAndSend(
                eq(USER_EXCHANGE), eq(""), any(), any(MessagePostProcessor.class));
    }
}
