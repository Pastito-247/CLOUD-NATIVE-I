package com.ecommerce.users.config;

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
 * Configuracion de RabbitMQ del dominio de notificaciones.
 *
 * Declara el exchange de pedidos, la cola de notificaciones, su binding y su
 * dead-letter (DLX/DLQ). Los nombres provienen de application.yml; las
 * declaraciones son idempotentes y deben coincidir con orders-service.
 */
@Configuration
@EnableConfigurationProperties(MessagingProperties.class)
public class RabbitMQConfig {

    private final MessagingProperties props;

    public RabbitMQConfig(MessagingProperties props) {
        this.props = props;
    }

    @Bean
    public TopicExchange ordersExchange() {
        return new TopicExchange(props.getExchange().getOrders(), true, false);
    }

    @Bean
    public DirectExchange ordersDeadLetterExchange() {
        return new DirectExchange(props.getExchange().getDeadLetter(), true, false);
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(props.getQueue().getNotification())
                .deadLetterExchange(props.getExchange().getDeadLetter())
                .deadLetterRoutingKey(props.getRoutingKey().getNotificationDlq())
                .build();
    }

    @Bean
    public Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable(props.getQueue().getNotificationDlq()).build();
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue())
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
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
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
