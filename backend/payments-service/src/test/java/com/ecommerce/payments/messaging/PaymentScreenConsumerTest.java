package com.ecommerce.payments.messaging;

import com.ecommerce.payments.config.MessagingProperties;
import com.ecommerce.payments.event.OrderItemEvent;
import com.ecommerce.payments.event.PaymentRequestedEvent;
import com.ecommerce.payments.event.ReceiptGeneratedEvent;
import com.ecommerce.payments.model.Payment;
import com.ecommerce.payments.model.Receipt;
import com.ecommerce.payments.service.PaymentService;
import com.ecommerce.payments.service.ReceiptService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentScreenConsumerTest {

    private static final long DELIVERY_TAG = 11L;

    private final PaymentService paymentService = mock(PaymentService.class);
    private final ReceiptService receiptService = mock(ReceiptService.class);
    private final PaymentEventPublisher publisher = mock(PaymentEventPublisher.class);
    private final Channel channel = mock(Channel.class);

    private MessagingProperties props;
    private PaymentScreenConsumer consumer;

    @BeforeEach
    void setUp() {
        props = new MessagingProperties();
        props.getQueue().setPaymentScreen("payment.screen.queue");
        props.getQueue().setPaymentScreenDlq("payment.screen.dlq");
        props.getProcessing().setDelayMs(0);
        props.getRetry().setMaxAttempts(3);
        props.getRetry().setDelayMs(0);
        consumer = new PaymentScreenConsumer(paymentService, receiptService, publisher, props);
    }

    private PaymentRequestedEvent event(String email) {
        return new PaymentRequestedEvent(1L, 10L, email, BigDecimal.TEN, "CREDIT_CARD",
                "Av. Siempre Viva 742",
                List.of(new OrderItemEvent(5L, "Teclado", 1, BigDecimal.TEN)));
    }

    private Message messageWithRetry(int retryCount) {
        MessageProperties properties = new MessageProperties();
        properties.setHeader("x-retry-count", retryCount);
        return new Message("{}".getBytes(), properties);
    }

    private Payment completedPayment() {
        Payment payment = new Payment(10L, "cliente@tuki.cl", BigDecimal.TEN, "CREDIT_CARD", "dir", "[]");
        payment.setId(1L);
        payment.setStatus(Payment.STATUS_COMPLETED);
        return payment;
    }

    @Test
    void procesaCorrectamente_generaBoletaPublicaYAck() throws Exception {
        Payment payment = completedPayment();
        Receipt receipt = new Receipt(1L, 10L, "cliente@tuki.cl", BigDecimal.TEN, "receipts/boleta-pago-1.pdf", "[]");
        receipt.setId(99L);

        when(paymentService.markCompleted(1L)).thenReturn(payment);
        when(receiptService.generateReceipt(eq(payment), any())).thenReturn(receipt);

        consumer.handlePaymentRequested(event("cliente@tuki.cl"), messageWithRetry(0), channel, DELIVERY_TAG);

        verify(publisher).publishReceiptGenerated(any(ReceiptGeneratedEvent.class));
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(DELIVERY_TAG, false, false);
        verify(publisher, never()).republishToQueue(any(), any(), anyInt());
    }

    @Test
    void fallaConIntentosDisponibles_reencolaYAck() throws Exception {
        PaymentRequestedEvent event = event("fail@tuki.cl");

        consumer.handlePaymentRequested(event, messageWithRetry(0), channel, DELIVERY_TAG);

        verify(publisher).republishToQueue(eq("payment.screen.queue"), eq(event), eq(1));
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    void fallaSinIntentos_marcaFallidoYEnviaADlq() throws Exception {
        PaymentRequestedEvent event = event("fail@tuki.cl");

        consumer.handlePaymentRequested(event, messageWithRetry(3), channel, DELIVERY_TAG);

        verify(paymentService).markFailed(1L);
        verify(channel).basicNack(DELIVERY_TAG, false, false);
        verify(publisher, never()).republishToQueue(any(), any(), anyInt());
    }
}
