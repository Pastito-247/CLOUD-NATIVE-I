package com.ecommerce.orders.messaging;

import com.ecommerce.orders.config.MessagingProperties;
import com.ecommerce.orders.event.OrderCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifica que el productor publique order.created en el exchange fanout de
 * pedidos (nombre centralizado en application.yml).
 */
class OrderEventPublisherTest {

    private static final String ORDER_EXCHANGE = "order.exchange";

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

    private OrderEventPublisher publisher;

    @BeforeEach
    void setUp() {
        MessagingProperties props = new MessagingProperties();
        props.getExchange().setOrder(ORDER_EXCHANGE);
        publisher = new OrderEventPublisher(rabbitTemplate, props);
    }

    @Test
    void publicaPedidoCreado() {
        publisher.publishOrderCreated(
                new OrderCreatedEvent(1L, "cliente@tuki.cl", BigDecimal.TEN, "Calle 1", LocalDateTime.now()));

        verify(rabbitTemplate).convertAndSend(
                eq(ORDER_EXCHANGE), eq(""), any(), any(MessagePostProcessor.class));
    }
}
