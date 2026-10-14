package com.ecommerce.users.messaging;

import com.ecommerce.users.config.MessagingProperties;
import com.ecommerce.users.event.OrderCreatedEvent;
import com.ecommerce.users.service.NotificationService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderCreatedConsumerTest {

    private static final long DELIVERY_TAG = 9L;

    private final NotificationService notificationService = mock(NotificationService.class);
    private final UserEventPublisher publisher = mock(UserEventPublisher.class);
    private final Channel channel = mock(Channel.class);

    private OrderCreatedConsumer consumer;

    @BeforeEach
    void setUp() {
        MessagingProperties props = new MessagingProperties();
        props.getQueue().setOrderCreated("order.created.queue");
        props.getQueue().setOrderCreatedDlq("order.created.dlq");
        props.getRetry().setMaxAttempts(3);
        props.getRetry().setDelayMs(0);
        consumer = new OrderCreatedConsumer(notificationService, publisher, props);
    }

    private OrderCreatedEvent event() {
        return new OrderCreatedEvent(1L, "cliente@tuki.cl", BigDecimal.TEN, "Calle 1", LocalDateTime.now());
    }

    private Message messageWithRetry(int retryCount) {
        MessageProperties properties = new MessageProperties();
        properties.setHeader("x-retry-count", retryCount);
        return new Message("{}".getBytes(), properties);
    }

    @Test
    void procesaCorrectamente_haceAck() throws Exception {
        consumer.handleOrderCreated(event(), messageWithRetry(0), channel, DELIVERY_TAG);

        verify(notificationService).sendOrderReceivedNotification(any());
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    void fallaConIntentosDisponibles_reencolaYAck() throws Exception {
        OrderCreatedEvent event = event();
        when(notificationService.sendOrderReceivedNotification(any()))
                .thenThrow(new RuntimeException("boom"));

        consumer.handleOrderCreated(event, messageWithRetry(0), channel, DELIVERY_TAG);

        verify(publisher).republishToQueue(eq("order.created.queue"), eq(event), eq(1));
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    void fallaSinIntentos_enviaADlq() throws Exception {
        OrderCreatedEvent event = event();
        when(notificationService.sendOrderReceivedNotification(any()))
                .thenThrow(new RuntimeException("boom"));

        consumer.handleOrderCreated(event, messageWithRetry(3), channel, DELIVERY_TAG);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
        verify(publisher, never()).republishToQueue(any(), any(), anyInt());
    }
}
