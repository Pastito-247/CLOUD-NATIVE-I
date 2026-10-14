package com.ecommerce.orders.messaging;

import com.ecommerce.orders.config.MessagingHeaders;
import com.ecommerce.orders.config.MessagingProperties;
import com.ecommerce.orders.event.ReceiptGeneratedEvent;
import com.ecommerce.orders.service.OrderReviewService;
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
 * Consumidor de la cola de revision del pedido.
 *
 * Escucha {@code order.review.queue}, deja el pedido listo para revision del
 * staff/administrador y confirma el mensaje de forma explicita. Ante un fallo
 * reintenta republicando la cola y, agotados los intentos, envia el mensaje a
 * la DLQ mediante NACK sin requeue.
 */
@Component
public class OrderReviewConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderReviewConsumer.class);

    private final OrderReviewService orderReviewService;
    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties props;

    public OrderReviewConsumer(OrderReviewService orderReviewService,
                               RabbitTemplate rabbitTemplate,
                               MessagingProperties props) {
        this.orderReviewService = orderReviewService;
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    @RabbitListener(queues = "${messaging.queue.order-review}")
    public void handleReceiptGenerated(ReceiptGeneratedEvent event,
                                       Message message,
                                       Channel channel,
                                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        int attempts = resolveRetryCount(message);
        try {
            orderReviewService.saveForReview(event);
            channel.basicAck(deliveryTag, false);
            log.info("Boleta {} procesada: pedido para revision guardado (intento {})",
                    event.receiptId(), attempts + 1);

        } catch (Exception ex) {
            log.error("Fallo guardando el pedido en revision de la boleta {} (intento {}): {}",
                    event.receiptId(), attempts + 1, ex.getMessage(), ex);

            if (attempts < props.getRetry().getMaxAttempts()) {
                sleepBetweenRetries();
                republish(attempts + 1, event);
                channel.basicAck(deliveryTag, false);
                log.warn("Mensaje de la boleta {} reencolado para reintento {}/{}",
                        event.receiptId(), attempts + 1, props.getRetry().getMaxAttempts());
            } else {
                log.error("Reintentos agotados para la boleta {}. Enviando a la DLQ '{}'",
                        event.receiptId(), props.getQueue().getOrderReviewDlq());
                // requeue=false -> el broker enruta el mensaje al dead-letter exchange.
                channel.basicNack(deliveryTag, false, false);
            }
        }
    }

    private void republish(int retryCount, ReceiptGeneratedEvent event) {
        rabbitTemplate.convertAndSend("", props.getQueue().getOrderReview(), event, m -> {
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
