package com.ecommerce.orders.config;

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
 * Configuracion de RabbitMQ del dominio de pedidos.
 *
 * Declara, mediante beans, el exchange, las colas, los bindings y el
 * dead-letter (DLX/DLQ) de cada caso de uso. Toda la infraestructura de
 * mensajeria queda aislada aqui; los servicios de dominio no conocen
 * detalles de RabbitMQ.
 *
 * Topologia:
 *   orders.exchange (topic)
 *     --(order.created)--> order.notification.queue  -> consumida por users-service
 *     --(order.created)--> order.ticket.queue        -> consumida por orders-service
 *   orders.dlx (direct) -> order.notification.dlq / order.ticket.dlq
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
    public TopicExchange ordersExchange() {
        return new TopicExchange(props.getExchange().getOrders(), true, false);
    }

    @Bean
    public DirectExchange ordersDeadLetterExchange() {
        return new DirectExchange(props.getExchange().getDeadLetter(), true, false);
    }

    // ------------------------------------------------------------------
    // Colas de negocio (con dead-letter hacia orders.dlx)
    // ------------------------------------------------------------------

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(props.getQueue().getNotification())
                .deadLetterExchange(props.getExchange().getDeadLetter())
                .deadLetterRoutingKey(props.getRoutingKey().getNotificationDlq())
                .build();
    }

    @Bean
    public Queue ticketQueue() {
        return QueueBuilder.durable(props.getQueue().getTicket())
                .deadLetterExchange(props.getExchange().getDeadLetter())
                .deadLetterRoutingKey(props.getRoutingKey().getTicketDlq())
                .build();
    }

    // ------------------------------------------------------------------
    // Colas muertas (DLQ)
    // ------------------------------------------------------------------

    @Bean
    public Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable(props.getQueue().getNotificationDlq()).build();
    }

    @Bean
    public Queue ticketDeadLetterQueue() {
        return QueueBuilder.durable(props.getQueue().getTicketDlq()).build();
    }

    // ------------------------------------------------------------------
    // Bindings
    // ------------------------------------------------------------------

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue())
                .to(ordersExchange())
                .with(props.getRoutingKey().getOrderCreated());
    }

    @Bean
    public Binding ticketBinding() {
        return BindingBuilder.bind(ticketQueue())
                .to(ordersExchange())
                .with(props.getRoutingKey().getOrderCreated());
    }

    @Bean
    public Binding notificationDeadLetterBinding() {
        return BindingBuilder.bind(notificationDeadLetterQueue())
                .to(ordersDeadLetterExchange())
                .with(props.getRoutingKey().getNotificationDlq());
    }

    @Bean
    public Binding ticketDeadLetterBinding() {
        return BindingBuilder.bind(ticketDeadLetterQueue())
                .to(ordersDeadLetterExchange())
                .with(props.getRoutingKey().getTicketDlq());
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
