package com.ecommerce.payments.messaging;

import com.ecommerce.payments.config.MessagingHeaders;
import com.ecommerce.payments.config.MessagingProperties;
import com.ecommerce.payments.event.PaymentRequestedEvent;
import com.ecommerce.payments.event.ReceiptGeneratedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Productor de eventos del dominio de pagos. Encapsula el envio a RabbitMQ
 * para que la capa de negocio no dependa de la API de mensajeria.
 */
@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties props;

    public PaymentEventPublisher(RabbitTemplate rabbitTemplate, MessagingProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    /**
     * Publica el evento {@code payment.requested} en el exchange fanout de pagos.
     * Un fallo de mensajeria no debe romper la creacion del pago.
     */
    public void publishPaymentRequested(PaymentRequestedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    props.getExchange().getPayment(),
                    "",
                    event,
                    message -> {
                        message.getMessageProperties().setHeader(
                                MessagingHeaders.ORIGINAL_TIMESTAMP, LocalDateTime.now().toString());
                        return message;
                    });
            log.info("Evento payment.requested publicado para el pago {}", event.paymentId());
        } catch (Exception ex) {
            log.error("No se pudo publicar payment.requested del pago {}: {}",
                    event.paymentId(), ex.getMessage());
        }
    }

    /**
     * Publica el evento {@code receipt.generated} en el exchange fanout de boletas.
     */
    public void publishReceiptGenerated(ReceiptGeneratedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    props.getExchange().getReceipt(),
                    "",
                    event,
                    message -> {
                        message.getMessageProperties().setHeader(
                                MessagingHeaders.ORIGINAL_TIMESTAMP, LocalDateTime.now().toString());
                        return message;
                    });
            log.info("Evento receipt.generated publicado para la boleta {} (pago {})",
                    event.receiptId(), event.paymentId());
        } catch (Exception ex) {
            log.error("No se pudo publicar receipt.generated de la boleta {}: {}",
                    event.receiptId(), ex.getMessage());
        }
    }

    /**
     * Reencola un mensaje en la cola indicada incrementando el contador de
     * reintentos. Se usa desde los consumidores cuando un procesamiento falla
     * y aun quedan intentos disponibles.
     */
    public void republishToQueue(String queue, Object event, int retryCount) {
        rabbitTemplate.convertAndSend("", queue, event, message -> {
            message.getMessageProperties().setHeader(MessagingHeaders.RETRY_COUNT, retryCount);
            return message;
        });
    }
}
