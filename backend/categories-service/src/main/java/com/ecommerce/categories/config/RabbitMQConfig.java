package com.ecommerce.categories.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper.TypePrecedence;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion de RabbitMQ del dominio de catalogo (lado categorias).
 *
 * Declara el exchange de catalogo, la cola de sincronizacion de categorias
 * y su dead-letter. La cola consume los eventos del dominio de productos
 * (product.*) para reaccionar a cambios en el catalogo; los eventos de
 * categoria (category.*) los produce este mismo servicio.
 *
 * Topologia:
 *   catalog.exchange (topic)
 *     --(product.created|updated|deleted)--> category.sync.queue
 *   catalog.dlx (direct) -> category.sync.dlq
 */
@Configuration
@EnableConfigurationProperties(MessagingProperties.class)
public class RabbitMQConfig {

    private final MessagingProperties props;

    public RabbitMQConfig(MessagingProperties props) {
        this.props = props;
    }

    // ------------------------------------------------------------------
    // Exchanges
    // ------------------------------------------------------------------

    @Bean
    public TopicExchange catalogExchange() {
        return new TopicExchange(props.getExchange().getCatalog(), true, false);
    }

    @Bean
    public DirectExchange catalogDeadLetterExchange() {
        return new DirectExchange(props.getExchange().getDeadLetter(), true, false);
    }

    // ------------------------------------------------------------------
    // Colas de negocio (con dead-letter hacia catalog.dlx)
    // ------------------------------------------------------------------

    @Bean
    public Queue categorySyncQueue() {
        return QueueBuilder.durable(props.getQueue().getCategorySync())
                .deadLetterExchange(props.getExchange().getDeadLetter())
                .deadLetterRoutingKey(props.getRoutingKey().getCategorySyncDlq())
                .build();
    }

    @Bean
    public Queue categorySyncDeadLetterQueue() {
        return QueueBuilder.durable(props.getQueue().getCategorySyncDlq()).build();
    }

    // ------------------------------------------------------------------
    // Bindings (la cola de categorias se suscribe a los eventos de producto)
    // ------------------------------------------------------------------

    @Bean
    public Binding categorySyncProductCreatedBinding() {
        return BindingBuilder.bind(categorySyncQueue())
                .to(catalogExchange())
                .with(props.getRoutingKey().getProductCreated());
    }

    @Bean
    public Binding categorySyncProductUpdatedBinding() {
        return BindingBuilder.bind(categorySyncQueue())
                .to(catalogExchange())
                .with(props.getRoutingKey().getProductUpdated());
    }

    @Bean
    public Binding categorySyncProductDeletedBinding() {
        return BindingBuilder.bind(categorySyncQueue())
                .to(catalogExchange())
                .with(props.getRoutingKey().getProductDeleted());
    }

    @Bean
    public Binding categorySyncDeadLetterBinding() {
        return BindingBuilder.bind(categorySyncDeadLetterQueue())
                .to(catalogDeadLetterExchange())
                .with(props.getRoutingKey().getCategorySyncDlq());
    }

    // ------------------------------------------------------------------
    // Serializacion JSON + RabbitTemplate
    // ------------------------------------------------------------------

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        // El tipo se infiere del parametro del @RabbitListener; evita depender
        // del header __TypeId__ (los servicios viven en paquetes distintos).
        converter.setTypePrecedence(TypePrecedence.INFERRED);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        template.setMandatory(true);
        return template;
    }
}
