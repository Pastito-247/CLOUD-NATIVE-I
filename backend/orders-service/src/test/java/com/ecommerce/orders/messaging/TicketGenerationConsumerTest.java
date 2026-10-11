package com.ecommerce.orders.messaging;

import com.ecommerce.orders.config.MessagingProperties;
import com.ecommerce.orders.event.OrderCreatedEvent;
import com.ecommerce.orders.service.TicketService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketGenerationConsumerTest {

    private static final long DELIVERY_TAG = 7L;

    private final TicketService ticketService = mock(TicketService.class);
    private final OrderEventPublisher publisher = mock(OrderEventPublisher.class);
    private final Channel channel = mock(Channel.class);

    private MessagingProperties props;
    private TicketGenerationConsumer consumer;

    @BeforeEach
    void setUp() {
        props = new MessagingProperties();
        props.getQueue().setTicket("order.ticket.queue");
        props.getQueue().setTicketDlq("order.ticket.dlq");
        props.getRetry().setMaxAttempts(3);
        props.getRetry().setDelayMs(0);
        consumer = new TicketGenerationConsumer(ticketService, publisher, props);
    }

    private OrderCreatedEvent event() {
        return new OrderCreatedEvent(1L, "cliente@tuki.cl", BigDecimal.TEN,
                "PENDING", "Av. Siempre Viva 742", null);
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
        verify(publisher, never()).republishToQueue(any(), any(), anyInt());
    }

    @Test
    void fallaConIntentosDisponibles_reencolaYAck() throws Exception {
        OrderCreatedEvent event = event();
        when(ticketService.generateTicket(event)).thenThrow(new RuntimeException("boom"));

        consumer.handleOrderCreated(event, messageWithRetry(0), channel, DELIVERY_TAG);

        verify(publisher).republishToQueue(eq("order.ticket.queue"), eq(event), eq(1));
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    void fallaSinIntentos_enviaADlq() throws Exception {
        OrderCreatedEvent event = event();
        when(ticketService.generateTicket(event)).thenThrow(new RuntimeException("boom"));

        consumer.handleOrderCreated(event, messageWithRetry(3), channel, DELIVERY_TAG);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
        verify(publisher, never()).republishToQueue(any(), any(), anyInt());
    }
}
