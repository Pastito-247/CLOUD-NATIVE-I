package com.ecommerce.users.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion centralizada de mensajeria para el dominio de usuarios.
 * Los nombres se leen desde application.yml (seccion {@code messaging}).
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
        private String orders;
        private String deadLetter;

        public String getOrders() {
            return orders;
        }

        public void setOrders(String orders) {
            this.orders = orders;
        }

        public String getDeadLetter() {
            return deadLetter;
        }

        public void setDeadLetter(String deadLetter) {
            this.deadLetter = deadLetter;
        }
    }

    public static class Queue {
        private String notification;
        private String notificationDlq;

        public String getNotification() {
            return notification;
        }

        public void setNotification(String notification) {
            this.notification = notification;
        }

        public String getNotificationDlq() {
            return notificationDlq;
        }

        public void setNotificationDlq(String notificationDlq) {
            this.notificationDlq = notificationDlq;
        }
    }

    public static class RoutingKey {
        private String orderCreated;
        private String notificationDlq;

        public String getOrderCreated() {
            return orderCreated;
        }

        public void setOrderCreated(String orderCreated) {
            this.orderCreated = orderCreated;
        }

        public String getNotificationDlq() {
            return notificationDlq;
        }

        public void setNotificationDlq(String notificationDlq) {
            this.notificationDlq = notificationDlq;
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
