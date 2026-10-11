package com.ecommerce.users.messaging;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Consumidor de la Dead Letter Queue de notificaciones. Registra en el log
 * los mensajes no entregados y los confirma para no bloquear la cola.
 */
@Component
public class DeadLetterLogConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterLogConsumer.class);

    @RabbitListener(queues = "${messaging.queue.notification-dlq}")
    public void onNotificationDeadLetter(Message message,
                                         Channel channel,
                                         @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        Object reason = message.getMessageProperties().getHeader("x-death");
        log.error("[DLQ:notification] Mensaje no entregado registrado. motivo={} payload={}",
                reason, body);
        channel.basicAck(deliveryTag, false);
    }
}
