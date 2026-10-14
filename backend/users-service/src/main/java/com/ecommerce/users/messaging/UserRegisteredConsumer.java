package com.ecommerce.users.messaging;

import com.ecommerce.users.config.MessagingHeaders;
import com.ecommerce.users.config.MessagingProperties;
import com.ecommerce.users.event.UserRegisteredEvent;
import com.ecommerce.users.service.NotificationService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumidor de la cola de usuarios registrados.
 *
 * Escucha {@code user.registered.queue} (publicada por este mismo servicio en
 * {@code user.exchange} al registrar una cuenta) y genera el correo de
 * bienvenida de forma asincrona. Confirma explicitamente; ante un fallo
 * reintenta republicando la cola y, agotados los intentos, envia el mensaje a
 * la DLQ mediante NACK sin requeue.
 */
@Component
public class UserRegisteredConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserRegisteredConsumer.class);

    private final NotificationService notificationService;
    private final UserEventPublisher publisher;
    private final MessagingProperties props;

    public UserRegisteredConsumer(NotificationService notificationService,
                                  UserEventPublisher publisher,
                                  MessagingProperties props) {
        this.notificationService = notificationService;
        this.publisher = publisher;
        this.props = props;
    }

    @RabbitListener(queues = "${messaging.queue.user-registered}")
    public void handleUserRegistered(UserRegisteredEvent event,
                                     Message message,
                                     Channel channel,
                                     @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        int attempts = resolveRetryCount(message);
        try {
            notificationService.sendWelcomeNotification(event);
            channel.basicAck(deliveryTag, false);
            log.info("Usuario {} procesado: correo de bienvenida enviado (intento {})",
                    event.userId(), attempts + 1);

        } catch (Exception ex) {
            log.error("Fallo dando la bienvenida al usuario {} (intento {}): {}",
                    event.userId(), attempts + 1, ex.getMessage(), ex);

            if (attempts < props.getRetry().getMaxAttempts()) {
                sleepBetweenRetries();
                publisher.republishToQueue(props.getQueue().getUserRegistered(), event, attempts + 1);
                channel.basicAck(deliveryTag, false);
                log.warn("Mensaje del usuario {} reencolado para reintento {}/{}",
                        event.userId(), attempts + 1, props.getRetry().getMaxAttempts());
            } else {
                log.error("Reintentos agotados para el usuario {}. Enviando a la DLQ '{}'",
                        event.userId(), props.getQueue().getUserRegisteredDlq());
                // requeue=false -> el broker enruta el mensaje al dead-letter exchange.
                channel.basicNack(deliveryTag, false, false);
            }
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
