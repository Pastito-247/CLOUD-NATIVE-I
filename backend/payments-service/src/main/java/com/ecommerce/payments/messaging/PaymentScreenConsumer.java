package com.ecommerce.payments.messaging;

import com.ecommerce.payments.config.MessagingHeaders;
import com.ecommerce.payments.config.MessagingProperties;
import com.ecommerce.payments.event.PaymentRequestedEvent;
import com.ecommerce.payments.event.ReceiptGeneratedEvent;
import com.ecommerce.payments.model.Payment;
import com.ecommerce.payments.model.Receipt;
import com.ecommerce.payments.service.PaymentService;
import com.ecommerce.payments.service.ReceiptService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Consumidor de la cola de "pantalla/simulacion" del pago.
 *
 * Simula el procesamiento del pago (sin pasarela real) con un retardo, marca
 * el pago como COMPLETED, genera la boleta PDF y publica {@code receipt.generated}
 * en el segundo exchange fanout. Si el procesamiento falla:
 *   - reintenta republicando el mensaje (hasta max-attempts), o
 *   - lo envia a la DLQ mediante NACK sin requeue (lo captura payment.dlx).
 */
@Component
public class PaymentScreenConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentScreenConsumer.class);

    private final PaymentService paymentService;
    private final ReceiptService receiptService;
    private final PaymentEventPublisher publisher;
    private final MessagingProperties props;

    public PaymentScreenConsumer(PaymentService paymentService,
                                 ReceiptService receiptService,
                                 PaymentEventPublisher publisher,
                                 MessagingProperties props) {
        this.paymentService = paymentService;
        this.receiptService = receiptService;
        this.publisher = publisher;
        this.props = props;
    }

    @RabbitListener(queues = "${messaging.queue.payment-screen}")
    public void handlePaymentRequested(PaymentRequestedEvent event,
                                       Message message,
                                       Channel channel,
                                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        int attempts = resolveRetryCount(message);
        try {
            simulatePaymentProcessing(event);

            Payment payment = paymentService.markCompleted(event.paymentId());
            Receipt receipt = receiptService.generateReceipt(payment, event);

            publisher.publishReceiptGenerated(new ReceiptGeneratedEvent(
                    receipt.getId(),
                    payment.getId(),
                    payment.getOrderId(),
                    payment.getUserEmail(),
                    payment.getAmount(),
                    receipt.getPdfPath(),
                    event.items(),
                    LocalDateTime.now()));

            channel.basicAck(deliveryTag, false);
            log.info("Pago {} procesado: boleta {} generada y publicada (intento {})",
                    payment.getId(), receipt.getId(), attempts + 1);

        } catch (Exception ex) {
            log.error("Fallo procesando el pago {} (intento {}): {}",
                    event.paymentId(), attempts + 1, ex.getMessage(), ex);

            if (attempts < props.getRetry().getMaxAttempts()) {
                sleepBetweenRetries();
                publisher.republishToQueue(props.getQueue().getPaymentScreen(), event, attempts + 1);
                channel.basicAck(deliveryTag, false);
                log.warn("Mensaje del pago {} reencolado para reintento {}/{}",
                        event.paymentId(), attempts + 1, props.getRetry().getMaxAttempts());
            } else {
                log.error("Reintentos agotados para el pago {}. Enviando a la DLQ '{}'",
                        event.paymentId(), props.getQueue().getPaymentScreenDlq());
                try {
                    paymentService.markFailed(event.paymentId());
                } catch (Exception markEx) {
                    log.warn("No se pudo marcar como FAILED el pago {}: {}",
                            event.paymentId(), markEx.getMessage());
                }
                // requeue=false -> el broker enruta el mensaje al dead-letter exchange.
                // El nack SIEMPRE se ejecuta para no dejar el mensaje sin ack (poison message).
                channel.basicNack(deliveryTag, false, false);
            }
        }
    }

    /**
     * Simula la pasarela de pago: retardo artificial y fallo controlado para
     * demostrar la ruta de error (correos que empiezan con "fail@").
     */
    private void simulatePaymentProcessing(PaymentRequestedEvent event) {
        long delay = props.getProcessing().getDelayMs();
        log.info("[SIMULACION] Procesando pago {} de {} por ${} (retardo {} ms)",
                event.paymentId(), event.userEmail(), event.amount(), delay);
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        if (event.userEmail() != null && event.userEmail().startsWith("fail@")) {
            throw new IllegalStateException("Fallo simulado al procesar el pago " + event.paymentId());
        }
    }

    private int resolveRetryCount(Message message) {
        Object header = message.getMessageProperties().getHeader(MessagingHeaders.RETRY_COUNT);
        if (header instanceof Number number) {
            return number.intValue();
        }
        if (header != null) {
            try {
                return Integer.parseInt(header.toString());
            } catch (NumberFormatException ignored) {
                log.warn("Cabecera {} con valor no numerico: {}", MessagingHeaders.RETRY_COUNT, header);
            }
        }
        return 0;
    }

    private void sleepBetweenRetries() {
        try {
            Thread.sleep(props.getRetry().getDelayMs());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
