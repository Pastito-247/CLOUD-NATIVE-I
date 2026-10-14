package com.ecommerce.users.messaging;

import com.ecommerce.users.config.MessagingProperties;
import com.ecommerce.users.event.OrderItemEvent;
import com.ecommerce.users.event.PaymentRequestedEvent;
import com.ecommerce.users.service.NotificationService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentNotificationConsumerTest {

    private static final long DELIVERY_TAG = 9L;

    private final NotificationService notificationService = mock(NotificationService.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final Channel channel = mock(Channel.class);

    private PaymentNotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        MessagingProperties props = new MessagingProperties();
        props.getQueue().setPaymentNotification("payment.notification.queue");
        props.getQueue().setPaymentNotificationDlq("payment.notification.dlq");
        props.getRetry().setMaxAttempts(3);
        props.getRetry().setDelayMs(0);
        consumer = new PaymentNotificationConsumer(notificationService, rabbitTemplate, props);
    }

    private PaymentRequestedEvent event() {
        return new PaymentRequestedEvent(2L, 10L, "cliente@tuki.cl", BigDecimal.ONE,
                "CREDIT_CARD", "direccion",
                List.of(new OrderItemEvent(5L, "Producto", 1, BigDecimal.ONE)));
    }

    private Message messageWithRetry(int retryCount) {
        MessageProperties properties = new MessageProperties();
        properties.setHeader("x-retry-count", retryCount);
        return new Message("{}".getBytes(), properties);
    }

    @Test
    void procesaCorrectamente_haceAck() throws Exception {
        consumer.handlePaymentRequested(event(), messageWithRetry(0), channel, DELIVERY_TAG);

        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    void fallaConIntentosDisponibles_reencolaYAck() throws Exception {
        PaymentRequestedEvent event = event();
        when(notificationService.sendPaymentProcessingNotification(event))
                .thenThrow(new RuntimeException("boom"));

        consumer.handlePaymentRequested(event, messageWithRetry(0), channel, DELIVERY_TAG);

        verify(rabbitTemplate).convertAndSend(
                eq(""), eq("payment.notification.queue"), eq(event), any(MessagePostProcessor.class));
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    void fallaSinIntentos_enviaADlq() throws Exception {
        PaymentRequestedEvent event = event();
        when(notificationService.sendPaymentProcessingNotification(event))
                .thenThrow(new RuntimeException("boom"));

        consumer.handlePaymentRequested(event, messageWithRetry(3), channel, DELIVERY_TAG);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
        verify(rabbitTemplate, never()).convertAndSend(
                eq(""), eq("payment.notification.queue"), eq(event), any(MessagePostProcessor.class));
    }
}
