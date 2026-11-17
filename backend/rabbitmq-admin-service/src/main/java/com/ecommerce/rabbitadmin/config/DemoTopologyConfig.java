package com.ecommerce.rabbitadmin.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DemoTopologyConfig {

    public static final String COMMANDS_EXCHANGE = "tuki.commands";
    public static final String CATALOG_EXCHANGE = "tuki.catalog.events";
    public static final String DLX_EXCHANGE = "tuki.dlx";

    public static final String ORDER_QUEUE = "tuki.notify.order.queue";
    public static final String PAYMENT_QUEUE = "tuki.notify.payment.queue";
    public static final String RECEIPT_QUEUE = "tuki.notify.receipt.queue";
    public static final String CATALOG_QUEUE = "tuki.catalog.sync.queue";

    public static final String ORDER_DLQ = "tuki.notify.order.dlq";
    public static final String PAYMENT_DLQ = "tuki.notify.payment.dlq";
    public static final String RECEIPT_DLQ = "tuki.notify.receipt.dlq";
    public static final String CATALOG_DLQ = "tuki.catalog.sync.dlq";

    public static final String ORDER_RK = "notify.order";
    public static final String PAYMENT_RK = "notify.payment";
    public static final String RECEIPT_RK = "notify.receipt";

    @Bean
    public DirectExchange tukiCommandsExchange() {
        return new DirectExchange(COMMANDS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange tukiCatalogEventsExchange() {
        return new TopicExchange(CATALOG_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange tukiDlxExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue tukiOrderQueue() {
        return demoQueue(ORDER_QUEUE, ORDER_DLQ);
    }

    @Bean
    public Queue tukiPaymentQueue() {
        return demoQueue(PAYMENT_QUEUE, PAYMENT_DLQ);
    }

    @Bean
    public Queue tukiReceiptQueue() {
        return demoQueue(RECEIPT_QUEUE, RECEIPT_DLQ);
    }

    @Bean
    public Queue tukiCatalogQueue() {
        return demoQueue(CATALOG_QUEUE, CATALOG_DLQ);
    }

    @Bean
    public Queue tukiOrderDlq() {
        return QueueBuilder.durable(ORDER_DLQ).build();
    }

    @Bean
    public Queue tukiPaymentDlq() {
        return QueueBuilder.durable(PAYMENT_DLQ).build();
    }

    @Bean
    public Queue tukiReceiptDlq() {
        return QueueBuilder.durable(RECEIPT_DLQ).build();
    }

    @Bean
    public Queue tukiCatalogDlq() {
        return QueueBuilder.durable(CATALOG_DLQ).build();
    }

    @Bean
    public Binding orderCommandBinding() {
        return BindingBuilder.bind(tukiOrderQueue()).to(tukiCommandsExchange()).with(ORDER_RK);
    }

    @Bean
    public Binding paymentCommandBinding() {
        return BindingBuilder.bind(tukiPaymentQueue()).to(tukiCommandsExchange()).with(PAYMENT_RK);
    }

    @Bean
    public Binding receiptCommandBinding() {
        return BindingBuilder.bind(tukiReceiptQueue()).to(tukiCommandsExchange()).with(RECEIPT_RK);
    }

    @Bean
    public Binding catalogProductBinding() {
        return BindingBuilder.bind(tukiCatalogQueue()).to(tukiCatalogEventsExchange()).with("product.*");
    }

    @Bean
    public Binding catalogCategoryBinding() {
        return BindingBuilder.bind(tukiCatalogQueue()).to(tukiCatalogEventsExchange()).with("category.*");
    }

    @Bean
    public Binding orderDlqBinding() {
        return dlqBinding(tukiOrderDlq(), ORDER_DLQ);
    }

    @Bean
    public Binding paymentDlqBinding() {
        return dlqBinding(tukiPaymentDlq(), PAYMENT_DLQ);
    }

    @Bean
    public Binding receiptDlqBinding() {
        return dlqBinding(tukiReceiptDlq(), RECEIPT_DLQ);
    }

    @Bean
    public Binding catalogDlqBinding() {
        return dlqBinding(tukiCatalogDlq(), CATALOG_DLQ);
    }

    private Queue demoQueue(String name, String dlq) {
        return QueueBuilder.durable(name)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", dlq)
                .build();
    }

    private Binding dlqBinding(Queue queue, String routingKey) {
        return BindingBuilder.bind(queue).to(tukiDlxExchange()).with(routingKey);
    }
}
