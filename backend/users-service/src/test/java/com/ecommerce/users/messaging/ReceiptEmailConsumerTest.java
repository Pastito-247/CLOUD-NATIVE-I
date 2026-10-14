package com.ecommerce.users.messaging;

import com.ecommerce.users.config.MessagingProperties;
import com.ecommerce.users.event.OrderItemEvent;
import com.ecommerce.users.event.ReceiptGeneratedEvent;
import com.ecommerce.users.service.NotificationService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceiptEmailConsumerTest {

    private static final long DELIVERY_TAG = 4L;

    private final NotificationService notificationService = mock(NotificationService.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final Channel channel = mock(Channel.class);

    private ReceiptEmailConsumer consumer;

    @BeforeEach
    void setUp() {
        MessagingProperties props = new MessagingProperties();
        props.getQueue().setReceiptEmail("receipt.email.queue");
        props.getQueue().setReceiptEmailDlq("receipt.email.dlq");
        props.getRetry().setMaxAttempts(3);
        props.getRetry().setDelayMs(0);
        consumer = new ReceiptEmailConsumer(notificationService, rabbitTemplate, props);
    }

    private ReceiptGeneratedEvent event() {
        return new ReceiptGeneratedEvent(1L, 2L, 10L, "cliente@tuki.cl", BigDecimal.TEN,
                "receipts/boleta-pago-2.pdf",
                List.of(new OrderItemEvent(5L, "Producto", 1, BigDecimal.TEN)),
                LocalDateTime.now());
    }

    private Message messageWithRetry(int retryCount) {
        MessageProperties properties = new MessageProperties();
        properties.setHeader("x-retry-count", retryCount);
        return new Message("{}".getBytes(), properties);
    }

    @Test
    void procesaCorrectamente_haceAck() throws Exception {
        consumer.handleReceiptGenerated(event(), messageWithRetry(0), channel, DELIVERY_TAG);

        verify(notificationService).sendOrderConfirmation(any());
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    void fallaConIntentosDisponibles_reencolaYAck() throws Exception {
        ReceiptGeneratedEvent event = event();
        when(notificationService.sendOrderConfirmation(event))
                .thenThrow(new RuntimeException("boom"));

        consumer.handleReceiptGenerated(event, messageWithRetry(0), channel, DELIVERY_TAG);

        verify(rabbitTemplate).convertAndSend(
                eq(""), eq("receipt.email.queue"), eq(event), any(MessagePostProcessor.class));
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    void fallaSinIntentos_enviaADlq() throws Exception {
        ReceiptGeneratedEvent event = event();
        when(notificationService.sendOrderConfirmation(event))
                .thenThrow(new RuntimeException("boom"));

        consumer.handleReceiptGenerated(event, messageWithRetry(3), channel, DELIVERY_TAG);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
        verify(rabbitTemplate, never()).convertAndSend(
                eq(""), eq("receipt.email.queue"), eq(event), any(MessagePostProcessor.class));
    }
}
