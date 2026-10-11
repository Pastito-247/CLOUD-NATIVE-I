package com.ecommerce.orders.messaging;

import com.ecommerce.orders.config.MessagingHeaders;
import com.ecommerce.orders.config.MessagingProperties;
import com.ecommerce.orders.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Productor de eventos de pedidos. Encapsula el envio a RabbitMQ para que la
 * capa de negocio (OrderService) no dependa de la API de mensajeria.
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties props;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate, MessagingProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    /**
     * Publica el evento {@code order.created} en el exchange de pedidos.
     * Un fallo de mensajeria no debe romper la creacion del pedido.
     */
    public void publishOrderCreated(OrderCreatedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    props.getExchange().getOrders(),
                    props.getRoutingKey().getOrderCreated(),
                    event,
                    message -> {
                        message.getMessageProperties().setHeader(
                                MessagingHeaders.ORIGINAL_TIMESTAMP, LocalDateTime.now().toString());
                        return message;
                    });
            log.info("Evento order.created publicado para el pedido {}", event.orderId());
        } catch (Exception ex) {
            log.error("No se pudo publicar order.created del pedido {}: {}",
                    event.orderId(), ex.getMessage());
        }
    }

    /**
     * Reencola un mensaje en la cola indicada incrementando el contador de
     * reintentos. Se usa desde los consumidores cuando un procesamiento falla
     * y aun quedan intentos disponibles.
     */
    public void republishToQueue(String queue, OrderCreatedEvent event, int retryCount) {
        rabbitTemplate.convertAndSend("", queue, event, message -> {
            message.getMessageProperties().setHeader(MessagingHeaders.RETRY_COUNT, retryCount);
            return message;
        });
    }
}
