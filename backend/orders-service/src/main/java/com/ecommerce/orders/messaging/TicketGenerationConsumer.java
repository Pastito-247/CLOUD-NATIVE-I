package com.ecommerce.orders.messaging;

import com.ecommerce.orders.config.MessagingHeaders;
import com.ecommerce.orders.config.MessagingProperties;
import com.ecommerce.orders.event.OrderCreatedEvent;
import com.ecommerce.orders.service.TicketService;
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
 * Consumidor del dominio de documentos/tickets.
 *
 * Escucha la cola de tickets, genera el ticket del pedido y confirma el
 * mensaje de forma explicita. Si el procesamiento falla:
 *   - reintenta republicando el mensaje (hasta max-attempts), o
 *   - lo envia a la DLQ mediante NACK sin requeue (lo captura orders.dlx).
 */
@Component
public class TicketGenerationConsumer {

    private static final Logger log = LoggerFactory.getLogger(TicketGenerationConsumer.class);

    private final TicketService ticketService;
    private final OrderEventPublisher publisher;
    private final MessagingProperties props;

    public TicketGenerationConsumer(TicketService ticketService,
                                    OrderEventPublisher publisher,
                                    MessagingProperties props) {
        this.ticketService = ticketService;
        this.publisher = publisher;
        this.props = props;
    }

    @RabbitListener(queues = "${messaging.queue.ticket}")
    public void handleOrderCreated(OrderCreatedEvent event,
                                   Message message,
                                   Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        int attempts = resolveRetryCount(message);
        try {
            ticketService.generateTicket(event);
            channel.basicAck(deliveryTag, false);
            log.info("Pedido {} procesado: ticket generado (intento {})",
                    event.orderId(), attempts + 1);

        } catch (Exception ex) {
            log.error("Fallo generando el ticket del pedido {} (intento {}): {}",
                    event.orderId(), attempts + 1, ex.getMessage(), ex);

            if (attempts < props.getRetry().getMaxAttempts()) {
                sleepBetweenRetries();
                publisher.republishToQueue(props.getQueue().getTicket(), event, attempts + 1);
                channel.basicAck(deliveryTag, false);
                log.warn("Mensaje del pedido {} reencolado para reintento {}/{}",
                        event.orderId(), attempts + 1, props.getRetry().getMaxAttempts());
            } else {
                log.error("Reintentos agotados para el pedido {}. Enviando a la DLQ '{}'",
                        event.orderId(), props.getQueue().getTicketDlq());
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
