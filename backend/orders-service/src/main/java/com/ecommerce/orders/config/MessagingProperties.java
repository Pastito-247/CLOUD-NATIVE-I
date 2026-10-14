package com.ecommerce.orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion centralizada de mensajeria para el dominio de pedidos.
 *
 * Los nombres de exchanges, colas y routing keys se leen desde
 * application.yml (seccion {@code messaging}). Ningun nombre debe quedar
 * escrito directamente en la logica de negocio.
 */
@ConfigurationProperties(prefix = "messaging")
public class MessagingProperties {

    private final Exchange exchange = new Exchange();
    private final Queue queue = new Queue();
    private final RoutingKey routingKey = new RoutingKey();
    private final Retry retry = new Retry();

    public Exchange getExchange() {
        return exchange;
    }

    public Queue getQueue() {
        return queue;
    }

    public RoutingKey getRoutingKey() {
        return routingKey;
    }

    public Retry getRetry() {
        return retry;
    }

    public static class Exchange {
        private String receipt;
        private String receiptDeadLetter;
        private String order;
        private String orderDeadLetter;

        public String getReceipt() {
            return receipt;
        }

        public void setReceipt(String receipt) {
            this.receipt = receipt;
        }

        public String getReceiptDeadLetter() {
            return receiptDeadLetter;
        }

        public void setReceiptDeadLetter(String receiptDeadLetter) {
            this.receiptDeadLetter = receiptDeadLetter;
        }

        public String getOrder() {
            return order;
        }

        public void setOrder(String order) {
            this.order = order;
        }

        public String getOrderDeadLetter() {
            return orderDeadLetter;
        }

        public void setOrderDeadLetter(String orderDeadLetter) {
            this.orderDeadLetter = orderDeadLetter;
        }
    }

    public static class Queue {
        private String orderReview;
        private String orderReviewDlq;
        private String orderCreated;
        private String orderCreatedDlq;

        public String getOrderReview() {
            return orderReview;
        }

        public void setOrderReview(String orderReview) {
            this.orderReview = orderReview;
        }

        public String getOrderReviewDlq() {
            return orderReviewDlq;
        }

        public void setOrderReviewDlq(String orderReviewDlq) {
            this.orderReviewDlq = orderReviewDlq;
        }

        public String getOrderCreated() {
            return orderCreated;
        }

        public void setOrderCreated(String orderCreated) {
            this.orderCreated = orderCreated;
        }

        public String getOrderCreatedDlq() {
            return orderCreatedDlq;
        }

        public void setOrderCreatedDlq(String orderCreatedDlq) {
            this.orderCreatedDlq = orderCreatedDlq;
        }
    }

    public static class RoutingKey {
        private String orderReviewDlq;
        private String orderCreatedDlq;

        public String getOrderReviewDlq() {
            return orderReviewDlq;
        }

        public void setOrderReviewDlq(String orderReviewDlq) {
            this.orderReviewDlq = orderReviewDlq;
        }

        public String getOrderCreatedDlq() {
            return orderCreatedDlq;
        }

        public void setOrderCreatedDlq(String orderCreatedDlq) {
            this.orderCreatedDlq = orderCreatedDlq;
        }
    }

    public static class Retry {
        private int maxAttempts = 3;
        private long delayMs = 500;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getDelayMs() {
            return delayMs;
        }

        public void setDelayMs(long delayMs) {
            this.delayMs = delayMs;
        }
    }
}
