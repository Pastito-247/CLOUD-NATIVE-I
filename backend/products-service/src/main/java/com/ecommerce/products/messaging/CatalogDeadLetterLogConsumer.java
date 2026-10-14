package com.ecommerce.products.messaging;

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
 * Consumidor de la Dead Letter Queue del dominio de catalogo (lado productos).
 *
 * Los mensajes que agotan sus reintentos o son rechazados terminan aqui.
 * Se registran en el log (con el motivo del rechazo, si RabbitMQ lo aporta)
 * y se confirman para no quedar atascados en la cola.
 */
@Component
public class CatalogDeadLetterLogConsumer {

    private static final Logger log = LoggerFactory.getLogger(CatalogDeadLetterLogConsumer.class);

    @RabbitListener(queues = "${messaging.queue.product-sync-dlq}")
    public void onProductSyncDeadLetter(Message message,
                                        Channel channel,
                                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        logDeadLetter("product-sync", message);
        channel.basicAck(deliveryTag, false);
    }

    @RabbitListener(queues = "${messaging.queue.stock-decrement-dlq}")
    public void onStockDecrementDeadLetter(Message message,
                                           Channel channel,
                                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        logDeadLetter("stock.decrement", message);
        channel.basicAck(deliveryTag, false);
    }

    private void logDeadLetter(String domain, Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        Object reason = message.getMessageProperties().getHeader("x-death");
        log.error("[DLQ:{}] Mensaje no entregado registrado. motivo={} payload={}",
                domain, reason, body);
    }
}
