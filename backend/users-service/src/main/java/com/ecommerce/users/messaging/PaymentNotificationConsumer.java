package com.ecommerce.users.messaging;

import com.ecommerce.users.config.MessagingHeaders;
import com.ecommerce.users.config.MessagingProperties;
import com.ecommerce.users.event.PaymentRequestedEvent;
import com.ecommerce.users.service.NotificationService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumidor de la cola de aviso de pago en proceso.
 *
 * Escucha {@code payment.notification.queue}, notifica que el pago esta en
 * proceso y confirma el mensaje de forma explicita. Ante un fallo reintenta
 * republicando la cola y, agotados los intentos, envia el mensaje a la DLQ
 * mediante NACK sin requeue.
 */
@Component
public class PaymentNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentNotificationConsumer.class);

    private final NotificationService notificationService;
    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties props;

    public PaymentNotificationConsumer(NotificationService notificationService,
                                       RabbitTemplate rabbitTemplate,
                                       MessagingProperties props) {
        this.notificationService = notificationService;
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    @RabbitListener(queues = "${messaging.queue.payment-notification}")
    public void handlePaymentRequested(PaymentRequestedEvent event,
                                       Message message,
                                       Channel channel,
                                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        int attempts = resolveRetryCount(message);
        try {
            notificationService.sendPaymentProcessingNotification(event);
            channel.basicAck(deliveryTag, false);
            log.info("Pago {} procesado: aviso enviado (intento {})",
                    event.paymentId(), attempts + 1);

        } catch (Exception ex) {
            log.error("Fallo enviando el aviso del pago {} (intento {}): {}",
                    event.paymentId(), attempts + 1, ex.getMessage(), ex);

            if (attempts < props.getRetry().getMaxAttempts()) {
                sleepBetweenRetries();
                republish(attempts + 1, event);
                channel.basicAck(deliveryTag, false);
                log.warn("Mensaje del pago {} reencolado para reintento {}/{}",
                        event.paymentId(), attempts + 1, props.getRetry().getMaxAttempts());
            } else {
                log.error("Reintentos agotados para el pago {}. Enviando a la DLQ '{}'",
                        event.paymentId(), props.getQueue().getPaymentNotificationDlq());
                // requeue=false -> el broker enruta el mensaje al dead-letter exchange.
                channel.basicNack(deliveryTag, false, false);
            }
        }
    }

    private void republish(int retryCount, PaymentRequestedEvent event) {
        rabbitTemplate.convertAndSend("", props.getQueue().getPaymentNotification(), event, m -> {
            m.getMessageProperties().setHeader(MessagingHeaders.RETRY_COUNT, retryCount);
            return m;
        });
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
