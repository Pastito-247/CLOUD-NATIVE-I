package com.ecommerce.products.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
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
 * Configuracion de RabbitMQ del dominio de catalogo (lado productos).
 *
 * Declara el exchange de catalogo, la cola de sincronizacion de productos
 * y su dead-letter. Ademas declara la cola de descuento de stock del flujo de
 * pago (consumida cuando se procesa un pago).
 *
 * Topologia:
 *   catalog.exchange (topic)
 *     --(category.created|updated|deleted)--> product.sync.queue
 *   catalog.dlx (direct) -> product.sync.dlq
 *
 *   receipt.exchange (fanout)
 *     --o stock.decrement.queue   -> descuento de stock
 *   receipt.dlx (direct) -> stock.decrement.dlq
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

    @Bean
    public FanoutExchange receiptExchange() {
        return new FanoutExchange(props.getExchange().getReceipt(), true, false);
    }

    @Bean
    public DirectExchange receiptDeadLetterExchange() {
        return new DirectExchange(props.getExchange().getReceiptDeadLetter(), true, false);
    }

    // ------------------------------------------------------------------
    // Colas de negocio (con dead-letter hacia catalog.dlx)
    // ------------------------------------------------------------------

    @Bean
    public Queue productSyncQueue() {
        return QueueBuilder.durable(props.getQueue().getProductSync())
                .deadLetterExchange(props.getExchange().getDeadLetter())
                .deadLetterRoutingKey(props.getRoutingKey().getProductSyncDlq())
                .build();
    }

    @Bean
    public Queue productSyncDeadLetterQueue() {
        return QueueBuilder.durable(props.getQueue().getProductSyncDlq()).build();
    }

    @Bean
    public Queue stockDecrementQueue() {
        return QueueBuilder.durable(props.getQueue().getStockDecrement())
                .deadLetterExchange(props.getExchange().getReceiptDeadLetter())
                .deadLetterRoutingKey(props.getRoutingKey().getStockDecrementDlq())
                .build();
    }

    @Bean
    public Queue stockDecrementDeadLetterQueue() {
        return QueueBuilder.durable(props.getQueue().getStockDecrementDlq()).build();
    }

    // ------------------------------------------------------------------
    // Bindings (la cola de productos se suscribe a los eventos de categoria)
    // ------------------------------------------------------------------

    @Bean
    public Binding productSyncCategoryCreatedBinding() {
        return BindingBuilder.bind(productSyncQueue())
                .to(catalogExchange())
                .with(props.getRoutingKey().getCategoryCreated());
    }

    @Bean
    public Binding productSyncCategoryUpdatedBinding() {
        return BindingBuilder.bind(productSyncQueue())
                .to(catalogExchange())
                .with(props.getRoutingKey().getCategoryUpdated());
    }

    @Bean
    public Binding productSyncCategoryDeletedBinding() {
        return BindingBuilder.bind(productSyncQueue())
                .to(catalogExchange())
                .with(props.getRoutingKey().getCategoryDeleted());
    }

    @Bean
    public Binding productSyncDeadLetterBinding() {
        return BindingBuilder.bind(productSyncDeadLetterQueue())
                .to(catalogDeadLetterExchange())
                .with(props.getRoutingKey().getProductSyncDlq());
    }

    @Bean
    public Binding stockDecrementBinding() {
        return BindingBuilder.bind(stockDecrementQueue()).to(receiptExchange());
    }

    @Bean
    public Binding stockDecrementDeadLetterBinding() {
        return BindingBuilder.bind(stockDecrementDeadLetterQueue())
                .to(receiptDeadLetterExchange())
                .with(props.getRoutingKey().getStockDecrementDlq());
    }

    // ------------------------------------------------------------------
    // Serializacion JSON + RabbitTemplate
    // ------------------------------------------------------------------

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        // Se reusa el ObjectMapper de Spring Boot para que JavaTimeModule
        // (LocalDateTime) quede registrado; el default no lo trae.
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
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
