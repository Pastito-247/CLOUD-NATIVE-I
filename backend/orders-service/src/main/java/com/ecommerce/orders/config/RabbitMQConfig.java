package com.ecommerce.orders.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper.TypePrecedence;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion de RabbitMQ del dominio de pedidos.
 *
 * Declara el exchange fanout de boletas (lado consumidor de order.review) y el
 * exchange fanout de pedidos (lado productor de order.created), junto con sus
 * colas, bindings y dead-letter (DLX/DLQ). Toda la infraestructura de
 * mensajeria queda aislada aqui; los servicios de dominio no conocen detalles
 * de RabbitMQ.
 *
 * Topologia:
 *   receipt.exchange (fanout)
 *     --o order.review.queue     -> pedido para revision staff/admin  (consume)
 *   receipt.dlx (direct) -> order.review.dlq
 *
 *   order.exchange (fanout)      -> publicado al crear un pedido (produce)
 *     --o order.created.queue    -> aviso "pedido recibido" (consume users-service)
 *   order.dlx (direct) -> order.created.dlq
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
    public FanoutExchange receiptExchange() {
        return new FanoutExchange(props.getExchange().getReceipt(), true, false);
    }

    @Bean
    public DirectExchange receiptDeadLetterExchange() {
        return new DirectExchange(props.getExchange().getReceiptDeadLetter(), true, false);
    }

    @Bean
    public FanoutExchange orderExchange() {
        return new FanoutExchange(props.getExchange().getOrder(), true, false);
    }

    @Bean
    public DirectExchange orderDeadLetterExchange() {
        return new DirectExchange(props.getExchange().getOrderDeadLetter(), true, false);
    }

    // ------------------------------------------------------------------
    // Colas de negocio (con dead-letter)
    // ------------------------------------------------------------------

    @Bean
    public Queue orderReviewQueue() {
        return QueueBuilder.durable(props.getQueue().getOrderReview())
                .deadLetterExchange(props.getExchange().getReceiptDeadLetter())
                .deadLetterRoutingKey(props.getRoutingKey().getOrderReviewDlq())
                .build();
    }

    @Bean
    public Queue orderCreatedQueue() {
        return QueueBuilder.durable(props.getQueue().getOrderCreated())
                .deadLetterExchange(props.getExchange().getOrderDeadLetter())
                .deadLetterRoutingKey(props.getRoutingKey().getOrderCreatedDlq())
                .build();
    }

    // ------------------------------------------------------------------
    // Colas muertas (DLQ)
    // ------------------------------------------------------------------

    @Bean
    public Queue orderReviewDeadLetterQueue() {
        return QueueBuilder.durable(props.getQueue().getOrderReviewDlq()).build();
    }

    @Bean
    public Queue orderCreatedDeadLetterQueue() {
        return QueueBuilder.durable(props.getQueue().getOrderCreatedDlq()).build();
    }

    // ------------------------------------------------------------------
    // Bindings
    // ------------------------------------------------------------------

    @Bean
    public Binding orderReviewBinding() {
        return BindingBuilder.bind(orderReviewQueue()).to(receiptExchange());
    }

    @Bean
    public Binding orderCreatedBinding() {
        return BindingBuilder.bind(orderCreatedQueue()).to(orderExchange());
    }

    @Bean
    public Binding orderReviewDeadLetterBinding() {
        return BindingBuilder.bind(orderReviewDeadLetterQueue())
                .to(receiptDeadLetterExchange())
                .with(props.getRoutingKey().getOrderReviewDlq());
    }

    @Bean
    public Binding orderCreatedDeadLetterBinding() {
        return BindingBuilder.bind(orderCreatedDeadLetterQueue())
                .to(orderDeadLetterExchange())
                .with(props.getRoutingKey().getOrderCreatedDlq());
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
