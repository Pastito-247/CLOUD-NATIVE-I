package com.ecommerce.categories.messaging;

import com.ecommerce.categories.config.MessagingProperties;
import com.ecommerce.categories.model.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifica que el productor publique los eventos de catalogo en el exchange y
 * la routing key configurados (nombres centralizados en application.yml).
 */
class CategoryEventPublisherTest {

    private static final String CATALOG_EXCHANGE = "catalog.exchange";

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

    private CategoryEventPublisher publisher;

    @BeforeEach
    void setUp() {
        MessagingProperties props = new MessagingProperties();
        props.getExchange().setCatalog(CATALOG_EXCHANGE);
        props.getRoutingKey().setCategoryCreated("category.created");
        props.getRoutingKey().setCategoryUpdated("category.updated");
        props.getRoutingKey().setCategoryDeleted("category.deleted");
        publisher = new CategoryEventPublisher(rabbitTemplate, props);
    }

    @Test
    void publicaEventoDeCreacion() {
        publisher.publishCategoryCreated(new Category("Tecnologia", "Equipos"));

        verify(rabbitTemplate).convertAndSend(
                eq(CATALOG_EXCHANGE), eq("category.created"), any(), any(MessagePostProcessor.class));
    }

    @Test
    void publicaEventoDeActualizacion() {
        publisher.publishCategoryUpdated(new Category("Tecnologia", "Actualizada"));

        verify(rabbitTemplate).convertAndSend(
                eq(CATALOG_EXCHANGE), eq("category.updated"), any(), any(MessagePostProcessor.class));
    }

    @Test
    void publicaEventoDeEliminacion() {
        publisher.publishCategoryDeleted(new Category("Tecnologia", "Equipos"));

        verify(rabbitTemplate).convertAndSend(
                eq(CATALOG_EXCHANGE), eq("category.deleted"), any(), any(MessagePostProcessor.class));
    }
}
