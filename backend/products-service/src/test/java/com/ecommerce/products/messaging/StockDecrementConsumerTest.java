package com.ecommerce.products.messaging;

import com.ecommerce.products.config.MessagingProperties;
import com.ecommerce.products.event.OrderItemEvent;
import com.ecommerce.products.event.ReceiptGeneratedEvent;
import com.ecommerce.products.service.ProductService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockDecrementConsumerTest {

    private static final long DELIVERY_TAG = 3L;

    private final ProductService productService = mock(ProductService.class);
    private final ProductEventPublisher publisher = mock(ProductEventPublisher.class);
    private final Channel channel = mock(Channel.class);

    private StockDecrementConsumer consumer;

    @BeforeEach
    void setUp() {
        MessagingProperties props = new MessagingProperties();
        props.getQueue().setStockDecrement("stock.decrement.queue");
        props.getQueue().setStockDecrementDlq("stock.decrement.dlq");
        props.getRetry().setMaxAttempts(3);
        props.getRetry().setDelayMs(0);
        consumer = new StockDecrementConsumer(productService, publisher, props);
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

        verify(productService).decrementStock(any());
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    void fallaConIntentosDisponibles_reencolaYAck() throws Exception {
        ReceiptGeneratedEvent event = event();
        when(productService.decrementStock(any())).thenThrow(new RuntimeException("sin stock"));

        consumer.handleReceiptGenerated(event, messageWithRetry(0), channel, DELIVERY_TAG);

        verify(publisher).republishToQueue(eq("stock.decrement.queue"), eq(event), eq(1));
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    void fallaSinIntentos_enviaADlq() throws Exception {
        ReceiptGeneratedEvent event = event();
        when(productService.decrementStock(any())).thenThrow(new RuntimeException("sin stock"));

        consumer.handleReceiptGenerated(event, messageWithRetry(3), channel, DELIVERY_TAG);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
        verify(publisher, never()).republishToQueue(any(), any(), anyInt());
    }
}
