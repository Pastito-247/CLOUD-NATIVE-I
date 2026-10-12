package com.ecommerce.categories.messaging;

import com.ecommerce.categories.config.MessagingHeaders;
import com.ecommerce.categories.config.MessagingProperties;
import com.ecommerce.categories.event.ProductChangedEvent;
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
 * Consumidor del dominio de catalogo (lado categorias).
 *
 * Escucha los eventos del dominio de productos (product.*) y confirma el
 * mensaje de forma explicita. Si el procesamiento falla:
 *   - reintenta republicando el mensaje (hasta max-attempts), o
 *   - lo envia a la DLQ mediante NACK sin requeue (lo captura catalog.dlx).
 */
@Component
public class ProductEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProductEventConsumer.class);

    private final CategoryEventPublisher publisher;
    private final MessagingProperties props;

    public ProductEventConsumer(CategoryEventPublisher publisher, MessagingProperties props) {
        this.publisher = publisher;
        this.props = props;
    }

    @RabbitListener(queues = "${messaging.queue.category-sync}")
    public void handleProductChanged(ProductChangedEvent event,
                                     Message message,
                                     Channel channel,
                                     @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        int attempts = resolveRetryCount(message);
        try {
            log.info("Producto sincronizado en categories-service: id={} accion={} nombre='{}' (intento {})",
                    event.productId(), event.changeType(), event.name(), attempts + 1);
            channel.basicAck(deliveryTag, false);

        } catch (Exception ex) {
            log.error("Fallo procesando el producto {} (intento {}): {}",
                    event.productId(), attempts + 1, ex.getMessage(), ex);

            if (attempts < props.getRetry().getMaxAttempts()) {
                sleepBetweenRetries();
                publisher.republishToQueue(props.getQueue().getCategorySync(), event, attempts + 1);
                channel.basicAck(deliveryTag, false);
                log.warn("Mensaje del producto {} reencolado para reintento {}/{}",
                        event.productId(), attempts + 1, props.getRetry().getMaxAttempts());
            } else {
                log.error("Reintentos agotados para el producto {}. Enviando a la DLQ '{}'",
                        event.productId(), props.getQueue().getCategorySyncDlq());
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
