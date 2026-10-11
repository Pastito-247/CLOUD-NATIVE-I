package com.ecommerce.products.messaging;

import com.ecommerce.products.config.MessagingHeaders;
import com.ecommerce.products.config.MessagingProperties;
import com.ecommerce.products.event.ProductChangedEvent;
import com.ecommerce.products.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Productor de eventos del dominio de catalogo (lado productos).
 *
 * Encapsula el envio a RabbitMQ para que la capa de negocio (ProductService)
 * no dependa de la API de mensajeria. Un fallo de mensajeria no debe romper
 * la operacion de catalogo.
 */
@Component
public class ProductEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProductEventPublisher.class);

    public static final String CREATED = "CREATED";
    public static final String UPDATED = "UPDATED";
    public static final String DELETED = "DELETED";

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties props;

    public ProductEventPublisher(RabbitTemplate rabbitTemplate, MessagingProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    public void publishProductCreated(Product product) {
        publish(props.getRoutingKey().getProductCreated(), product, CREATED);
    }

    public void publishProductUpdated(Product product) {
        publish(props.getRoutingKey().getProductUpdated(), product, UPDATED);
    }

    public void publishProductDeleted(Product product) {
        publish(props.getRoutingKey().getProductDeleted(), product, DELETED);
    }

    private void publish(String routingKey, Product product, String changeType) {
        ProductChangedEvent event = new ProductChangedEvent(
                product.getId(),
                product.getName(),
                product.getCategory(),
                product.getPrice(),
                product.getStockQuantity(),
                changeType,
                LocalDateTime.now());
        try {
            rabbitTemplate.convertAndSend(
                    props.getExchange().getCatalog(),
                    routingKey,
                    event,
                    message -> {
                        message.getMessageProperties().setHeader(
                                MessagingHeaders.ORIGINAL_TIMESTAMP, LocalDateTime.now().toString());
                        return message;
                    });
            log.info("Evento {} publicado para el producto {}", routingKey, product.getId());
        } catch (Exception ex) {
            log.error("No se pudo publicar {} del producto {}: {}",
                    routingKey, product.getId(), ex.getMessage());
        }
    }

    /**
     * Reencola un mensaje en la cola indicada incrementando el contador de
     * reintentos. Se usa desde los consumidores cuando un procesamiento falla
     * y aun quedan intentos disponibles.
     */
    public void republishToQueue(String queue, Object payload, int retryCount) {
        rabbitTemplate.convertAndSend("", queue, payload, message -> {
            message.getMessageProperties().setHeader(MessagingHeaders.RETRY_COUNT, retryCount);
            return message;
        });
    }
}
