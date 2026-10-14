package com.ecommerce.products.messaging;

import com.ecommerce.products.config.MessagingHeaders;
import com.ecommerce.products.config.MessagingProperties;
import com.ecommerce.products.event.ReceiptGeneratedEvent;
import com.ecommerce.products.service.ProductService;
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
 * Consumidor de la cola de descuento de stock.
 *
 * Escucha {@code stock.decrement.queue} (publicada en receipt.exchange cuando
 * el pago se proceso), descuenta el stock de los productos comprados y
 * confirma el mensaje de forma explicita. Ante un fallo reintenta republicando
 * la cola y, agotados los intentos, envia el mensaje a la DLQ mediante NACK sin
 * requeue.
 */
@Component
public class StockDecrementConsumer {

    private static final Logger log = LoggerFactory.getLogger(StockDecrementConsumer.class);

    private final ProductService productService;
    private final ProductEventPublisher publisher;
    private final MessagingProperties props;

    public StockDecrementConsumer(ProductService productService,
                                  ProductEventPublisher publisher,
                                  MessagingProperties props) {
        this.productService = productService;
        this.publisher = publisher;
        this.props = props;
    }

    @RabbitListener(queues = "${messaging.queue.stock-decrement}")
    public void handleReceiptGenerated(ReceiptGeneratedEvent event,
                                       Message message,
                                       Channel channel,
                                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        int attempts = resolveRetryCount(message);
        try {
            int affected = productService.decrementStock(event.items());
            channel.basicAck(deliveryTag, false);
            log.info("Boleta {} procesada: stock descontado de {} producto(s) (intento {})",
                    event.receiptId(), affected, attempts + 1);

        } catch (Exception ex) {
            log.error("Fallo descontando stock de la boleta {} (intento {}): {}",
                    event.receiptId(), attempts + 1, ex.getMessage(), ex);

            if (attempts < props.getRetry().getMaxAttempts()) {
                sleepBetweenRetries();
                publisher.republishToQueue(props.getQueue().getStockDecrement(), event, attempts + 1);
                channel.basicAck(deliveryTag, false);
                log.warn("Mensaje de la boleta {} reencolado para reintento {}/{}",
                        event.receiptId(), attempts + 1, props.getRetry().getMaxAttempts());
            } else {
                log.error("Reintentos agotados para la boleta {}. Enviando a la DLQ '{}'",
                        event.receiptId(), props.getQueue().getStockDecrementDlq());
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
