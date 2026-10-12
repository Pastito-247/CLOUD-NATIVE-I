package com.ecommerce.categories.messaging;

import com.ecommerce.categories.config.MessagingHeaders;
import com.ecommerce.categories.config.MessagingProperties;
import com.ecommerce.categories.event.CategoryChangedEvent;
import com.ecommerce.categories.model.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Productor de eventos del dominio de catalogo (lado categorias).
 *
 * Encapsula el envio a RabbitMQ para que la capa de negocio (CategoryService)
 * no dependa de la API de mensajeria. Un fallo de mensajeria no debe romper
 * la operacion de catalogo.
 */
@Component
public class CategoryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(CategoryEventPublisher.class);

    public static final String CREATED = "CREATED";
    public static final String UPDATED = "UPDATED";
    public static final String DELETED = "DELETED";

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties props;

    public CategoryEventPublisher(RabbitTemplate rabbitTemplate, MessagingProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    public void publishCategoryCreated(Category category) {
        publish(props.getRoutingKey().getCategoryCreated(), category, CREATED);
    }

    public void publishCategoryUpdated(Category category) {
        publish(props.getRoutingKey().getCategoryUpdated(), category, UPDATED);
    }

    public void publishCategoryDeleted(Category category) {
        publish(props.getRoutingKey().getCategoryDeleted(), category, DELETED);
    }

    private void publish(String routingKey, Category category, String changeType) {
        CategoryChangedEvent event = new CategoryChangedEvent(
                category.getId(),
                category.getName(),
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
            log.info("Evento {} publicado para la categoria {}", routingKey, category.getId());
        } catch (Exception ex) {
            log.error("No se pudo publicar {} de la categoria {}: {}",
                    routingKey, category.getId(), ex.getMessage());
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
