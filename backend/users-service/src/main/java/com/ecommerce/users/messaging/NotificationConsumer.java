package com.ecommerce.users.messaging;

import com.ecommerce.users.config.MessagingHeaders;
import com.ecommerce.users.config.MessagingProperties;
import com.ecommerce.users.event.OrderCreatedEvent;
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
 * Consumidor del dominio de notificaciones.
 *
 * Escucha los pedidos creados, envia la notificacion y confirma el mensaje de
 * forma explicita. Ante un fallo reintenta republishing la cola y, agotados
 * los intentos, envia el mensaje a la DLQ mediante NACK sin requeue.
 */
@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationService notificationService;
    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties props;

    public NotificationConsumer(NotificationService notificationService,
                                RabbitTemplate rabbitTemplate,
                                MessagingProperties props) {
        this.notificationService = notificationService;
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    @RabbitListener(queues = "${messaging.queue.notification}")
    public void handleOrderCreated(OrderCreatedEvent event,
                                   Message message,
                                   Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        int attempts = resolveRetryCount(message);
        try {
            notificationService.sendOrderNotification(event);
            channel.basicAck(deliveryTag, false);
            log.info("Pedido {} procesado: notificacion enviada (intento {})",
                    event.orderId(), attempts + 1);

        } catch (Exception ex) {
            log.error("Fallo enviando la notificacion del pedido {} (intento {}): {}",
                    event.orderId(), attempts + 1, ex.getMessage(), ex);

            if (attempts < props.getRetry().getMaxAttempts()) {
                sleepBetweenRetries();
                republish(attempts + 1, event);
                channel.basicAck(deliveryTag, false);
                log.warn("Mensaje del pedido {} reencolado para reintento {}/{}",
                        event.orderId(), attempts + 1, props.getRetry().getMaxAttempts());
            } else {
                log.error("Reintentos agotados para el pedido {}. Enviando a la DLQ '{}'",
                        event.orderId(), props.getQueue().getNotificationDlq());
                // requeue=false -> el broker enruta el mensaje al dead-letter exchange.
                channel.basicNack(deliveryTag, false, false);
            }
        }
    }

    private void republish(int retryCount, OrderCreatedEvent event) {
        rabbitTemplate.convertAndSend("", props.getQueue().getNotification(), event, m -> {
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
