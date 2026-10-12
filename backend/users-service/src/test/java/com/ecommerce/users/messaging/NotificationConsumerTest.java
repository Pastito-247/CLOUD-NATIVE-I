package com.ecommerce.users.messaging;

import com.ecommerce.users.config.MessagingProperties;
import com.ecommerce.users.event.OrderCreatedEvent;
import com.ecommerce.users.service.NotificationService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationConsumerTest {

    private static final long DELIVERY_TAG = 9L;

    private final NotificationService notificationService = mock(NotificationService.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final Channel channel = mock(Channel.class);

    private NotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        MessagingProperties props = new MessagingProperties();
        props.getQueue().setNotification("order.notification.queue");
        props.getQueue().setNotificationDlq("order.notification.dlq");
        props.getRetry().setMaxAttempts(3);
        props.getRetry().setDelayMs(0);
        consumer = new NotificationConsumer(notificationService, rabbitTemplate, props);
    }

    private OrderCreatedEvent event() {
        return new OrderCreatedEvent(2L, "cliente@tuki.cl", BigDecimal.ONE,
                "PENDING", "direccion", null);
    }

    private Message messageWithRetry(int retryCount) {
        MessageProperties properties = new MessageProperties();
        properties.setHeader("x-retry-count", retryCount);
        return new Message("{}".getBytes(), properties);
    }

    @Test
    void procesaCorrectamente_haceAck() throws Exception {
        consumer.handleOrderCreated(event(), messageWithRetry(0), channel, DELIVERY_TAG);

        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    void fallaConIntentosDisponibles_reencolaYAck() throws Exception {
        OrderCreatedEvent event = event();
        when(notificationService.sendOrderNotification(event))
                .thenThrow(new RuntimeException("boom"));

        consumer.handleOrderCreated(event, messageWithRetry(0), channel, DELIVERY_TAG);

        verify(rabbitTemplate).convertAndSend(
                eq(""), eq("order.notification.queue"), eq(event), any(MessagePostProcessor.class));
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    void fallaSinIntentos_enviaADlq() throws Exception {
        OrderCreatedEvent event = event();
        when(notificationService.sendOrderNotification(event))
                .thenThrow(new RuntimeException("boom"));

        consumer.handleOrderCreated(event, messageWithRetry(3), channel, DELIVERY_TAG);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
        verify(rabbitTemplate, never()).convertAndSend(
                eq(""), eq("order.notification.queue"), eq(event), any(MessagePostProcessor.class));
    }
}
