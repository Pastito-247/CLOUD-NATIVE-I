package com.ecommerce.payments.config;

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
 * Configuracion de RabbitMQ del dominio de pagos.
 *
 * Declara, mediante beans, los dos exchanges fanout del flujo de pago, sus
 * colas, bindings y el dead-letter (DLX/DLQ) de cada caso de uso. Toda la
 * infraestructura de mensajeria queda aislada aqui; los servicios de dominio
 * no conocen detalles de RabbitMQ.
 *
 * Topologia:
 *   payment.exchange (fanout)                                   [fase de pago]
 *     --o payment.notification.queue   -> aviso "pago en proceso"
 *     --o payment.screen.queue         -> simula el pago y genera la boleta PDF
 *   payment.dlx (direct) -> payment.notification.dlq / payment.screen.dlq
 *
 *   receipt.exchange (fanout)                                   [fase post-pago]
 *     --o receipt.email.queue          -> correo de confirmacion + boleta
 *     --o order.review.queue           -> pedido para revision staff/admin
 *     --o stock.decrement.queue        -> descuento de stock
 *   receipt.dlx (direct) -> receipt.email.dlq / order.review.dlq / stock.decrement.dlq
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
    public FanoutExchange receiptExchange() {
        return new FanoutExchange(props.getExchange().getReceipt(), true, false);
    }

    @Bean
    public DirectExchange paymentDeadLetterExchange() {
        return new DirectExchange(props.getExchange().getPaymentDeadLetter(), true, false);
    }

    @Bean
    public DirectExchange receiptDeadLetterExchange() {
        return new DirectExchange(props.getExchange().getReceiptDeadLetter(), true, false);
    }

    // ------------------------------------------------------------------
    // Colas de negocio de pago (con dead-letter hacia payment.dlx)
    // ------------------------------------------------------------------

    @Bean
    public Queue paymentNotificationQueue() {
        return QueueBuilder.durable(props.getQueue().getPaymentNotification())
                .deadLetterExchange(props.getExchange().getPaymentDeadLetter())
                .deadLetterRoutingKey(props.getRoutingKey().getPaymentNotificationDlq())
                .build();
    }

    @Bean
    public Queue paymentScreenQueue() {
        return QueueBuilder.durable(props.getQueue().getPaymentScreen())
                .deadLetterExchange(props.getExchange().getPaymentDeadLetter())
                .deadLetterRoutingKey(props.getRoutingKey().getPaymentScreenDlq())
                .build();
    }

    // ------------------------------------------------------------------
    // Colas de negocio post-pago (con dead-letter hacia receipt.dlx)
    // ------------------------------------------------------------------

    @Bean
    public Queue receiptEmailQueue() {
        return QueueBuilder.durable(props.getQueue().getReceiptEmail())
                .deadLetterExchange(props.getExchange().getReceiptDeadLetter())
                .deadLetterRoutingKey(props.getRoutingKey().getReceiptEmailDlq())
                .build();
    }

    @Bean
    public Queue orderReviewQueue() {
        return QueueBuilder.durable(props.getQueue().getOrderReview())
                .deadLetterExchange(props.getExchange().getReceiptDeadLetter())
                .deadLetterRoutingKey(props.getRoutingKey().getOrderReviewDlq())
                .build();
    }

    @Bean
    public Queue stockDecrementQueue() {
        return QueueBuilder.durable(props.getQueue().getStockDecrement())
                .deadLetterExchange(props.getExchange().getReceiptDeadLetter())
                .deadLetterRoutingKey(props.getRoutingKey().getStockDecrementDlq())
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
    public Queue paymentScreenDeadLetterQueue() {
        return QueueBuilder.durable(props.getQueue().getPaymentScreenDlq()).build();
    }

    @Bean
    public Queue receiptEmailDeadLetterQueue() {
        return QueueBuilder.durable(props.getQueue().getReceiptEmailDlq()).build();
    }

    @Bean
    public Queue orderReviewDeadLetterQueue() {
        return QueueBuilder.durable(props.getQueue().getOrderReviewDlq()).build();
    }

    @Bean
    public Queue stockDecrementDeadLetterQueue() {
        return QueueBuilder.durable(props.getQueue().getStockDecrementDlq()).build();
    }

    // ------------------------------------------------------------------
    // Bindings de negocio (fanout: sin routing key)
    // ------------------------------------------------------------------

    @Bean
    public Binding paymentNotificationBinding() {
        return BindingBuilder.bind(paymentNotificationQueue()).to(paymentExchange());
    }

    @Bean
    public Binding paymentScreenBinding() {
        return BindingBuilder.bind(paymentScreenQueue()).to(paymentExchange());
    }

    @Bean
    public Binding receiptEmailBinding() {
        return BindingBuilder.bind(receiptEmailQueue()).to(receiptExchange());
    }

    @Bean
    public Binding orderReviewBinding() {
        return BindingBuilder.bind(orderReviewQueue()).to(receiptExchange());
    }

    @Bean
    public Binding stockDecrementBinding() {
        return BindingBuilder.bind(stockDecrementQueue()).to(receiptExchange());
    }

    // ------------------------------------------------------------------
    // Bindings de las DLQ (direct: routing key del dead-letter)
    // ------------------------------------------------------------------

    @Bean
    public Binding paymentNotificationDeadLetterBinding() {
        return BindingBuilder.bind(paymentNotificationDeadLetterQueue())
                .to(paymentDeadLetterExchange())
                .with(props.getRoutingKey().getPaymentNotificationDlq());
    }

    @Bean
    public Binding paymentScreenDeadLetterBinding() {
        return BindingBuilder.bind(paymentScreenDeadLetterQueue())
                .to(paymentDeadLetterExchange())
                .with(props.getRoutingKey().getPaymentScreenDlq());
    }

    @Bean
    public Binding receiptEmailDeadLetterBinding() {
        return BindingBuilder.bind(receiptEmailDeadLetterQueue())
                .to(receiptDeadLetterExchange())
                .with(props.getRoutingKey().getReceiptEmailDlq());
    }

    @Bean
    public Binding orderReviewDeadLetterBinding() {
        return BindingBuilder.bind(orderReviewDeadLetterQueue())
                .to(receiptDeadLetterExchange())
                .with(props.getRoutingKey().getOrderReviewDlq());
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
