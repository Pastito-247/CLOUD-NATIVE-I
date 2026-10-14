package com.ecommerce.users.config;

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
 * Configuracion de RabbitMQ del dominio de usuarios/notificaciones.
 *
 * Declara los exchanges fanout del flujo de pago y de pedidos, las colas que
 * consume este servicio (aviso de pago, correo de confirmacion + boleta, aviso
 * de pedido recibido y bienvenida de usuario) y sus bindings con dead-letter
 * (DLX/DLQ). Los nombres provienen de application.yml; las declaraciones son
 * idempotentes y deben coincidir con el servicio productor.
 *
 * Topologia (lado consumidor):
 *   payment.exchange (fanout)  -> payment.notification.queue
 *   receipt.exchange (fanout)  -> receipt.email.queue
 *   order.exchange   (fanout)  -> order.created.queue
 *   user.exchange    (fanout)  -> user.registered.queue
 *
 * Cada cola de negocio tiene su DLX directo y su DLQ.
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
    public FanoutExchange paymentExchange() {
        return new FanoutExchange(props.getExchange().getPayment(), true, false);
    }

    @Bean
    public DirectExchange paymentDeadLetterExchange() {
        return new DirectExchange(props.getExchange().getPaymentDeadLetter(), true, false);
    }

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

    @Bean
    public FanoutExchange userExchange() {
        return new FanoutExchange(props.getExchange().getUser(), true, false);
    }

    @Bean
    public DirectExchange userDeadLetterExchange() {
        return new DirectExchange(props.getExchange().getUserDeadLetter(), true, false);
    }

    // ------------------------------------------------------------------
    // Colas de negocio (con dead-letter)
    // ------------------------------------------------------------------

    @Bean
    public Queue paymentNotificationQueue() {
        return QueueBuilder.durable(props.getQueue().getPaymentNotification())
                .deadLetterExchange(props.getExchange().getPaymentDeadLetter())
                .deadLetterRoutingKey(props.getRoutingKey().getPaymentNotificationDlq())
                .build();
    }

    @Bean
    public Queue receiptEmailQueue() {
        return QueueBuilder.durable(props.getQueue().getReceiptEmail())
                .deadLetterExchange(props.getExchange().getReceiptDeadLetter())
                .deadLetterRoutingKey(props.getRoutingKey().getReceiptEmailDlq())
                .build();
    }

    @Bean
    public Queue orderCreatedQueue() {
        return QueueBuilder.durable(props.getQueue().getOrderCreated())
                .deadLetterExchange(props.getExchange().getOrderDeadLetter())
                .deadLetterRoutingKey(props.getRoutingKey().getOrderCreatedDlq())
                .build();
    }

    @Bean
    public Queue userRegisteredQueue() {
        return QueueBuilder.durable(props.getQueue().getUserRegistered())
                .deadLetterExchange(props.getExchange().getUserDeadLetter())
                .deadLetterRoutingKey(props.getRoutingKey().getUserRegisteredDlq())
                .build();
    }

    // ------------------------------------------------------------------
    // Colas muertas (DLQ)
    // ------------------------------------------------------------------

    @Bean
    public Queue paymentNotificationDeadLetterQueue() {
        return QueueBuilder.durable(props.getQueue().getPaymentNotificationDlq()).build();
    }

    @Bean
    public Queue receiptEmailDeadLetterQueue() {
        return QueueBuilder.durable(props.getQueue().getReceiptEmailDlq()).build();
    }

    @Bean
    public Queue orderCreatedDeadLetterQueue() {
        return QueueBuilder.durable(props.getQueue().getOrderCreatedDlq()).build();
    }

    @Bean
    public Queue userRegisteredDeadLetterQueue() {
        return QueueBuilder.durable(props.getQueue().getUserRegisteredDlq()).build();
    }

    // ------------------------------------------------------------------
    // Bindings
    // ------------------------------------------------------------------

    @Bean
    public Binding paymentNotificationBinding() {
        return BindingBuilder.bind(paymentNotificationQueue()).to(paymentExchange());
    }

    @Bean
    public Binding receiptEmailBinding() {
        return BindingBuilder.bind(receiptEmailQueue()).to(receiptExchange());
    }

    @Bean
    public Binding orderCreatedBinding() {
        return BindingBuilder.bind(orderCreatedQueue()).to(orderExchange());
    }

    @Bean
    public Binding userRegisteredBinding() {
        return BindingBuilder.bind(userRegisteredQueue()).to(userExchange());
    }

    @Bean
    public Binding paymentNotificationDeadLetterBinding() {
        return BindingBuilder.bind(paymentNotificationDeadLetterQueue())
                .to(paymentDeadLetterExchange())
                .with(props.getRoutingKey().getPaymentNotificationDlq());
    }

    @Bean
    public Binding receiptEmailDeadLetterBinding() {
        return BindingBuilder.bind(receiptEmailDeadLetterQueue())
                .to(receiptDeadLetterExchange())
                .with(props.getRoutingKey().getReceiptEmailDlq());
    }

    @Bean
    public Binding orderCreatedDeadLetterBinding() {
        return BindingBuilder.bind(orderCreatedDeadLetterQueue())
                .to(orderDeadLetterExchange())
                .with(props.getRoutingKey().getOrderCreatedDlq());
    }

    @Bean
    public Binding userRegisteredDeadLetterBinding() {
        return BindingBuilder.bind(userRegisteredDeadLetterQueue())
                .to(userDeadLetterExchange())
                .with(props.getRoutingKey().getUserRegisteredDlq());
    }

    // ------------------------------------------------------------------
    // Serializacion JSON + RabbitTemplate
    // ------------------------------------------------------------------

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        // Se reusa el ObjectMapper de Spring Boot para registrar JavaTimeModule (LocalDateTime).
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
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
