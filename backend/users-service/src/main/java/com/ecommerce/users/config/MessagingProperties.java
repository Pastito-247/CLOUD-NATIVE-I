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
        private String payment;
        private String paymentDeadLetter;
        private String receipt;
        private String receiptDeadLetter;
        private String order;
        private String orderDeadLetter;
        private String user;
        private String userDeadLetter;

        public String getPayment() {
            return payment;
        }

        public void setPayment(String payment) {
            this.payment = payment;
        }

        public String getPaymentDeadLetter() {
            return paymentDeadLetter;
        }

        public void setPaymentDeadLetter(String paymentDeadLetter) {
            this.paymentDeadLetter = paymentDeadLetter;
        }

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

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getUserDeadLetter() {
            return userDeadLetter;
        }

        public void setUserDeadLetter(String userDeadLetter) {
            this.userDeadLetter = userDeadLetter;
        }
    }

    public static class Queue {
        private String paymentNotification;
        private String paymentNotificationDlq;
        private String receiptEmail;
        private String receiptEmailDlq;
        private String orderCreated;
        private String orderCreatedDlq;
        private String userRegistered;
        private String userRegisteredDlq;

        public String getPaymentNotification() {
            return paymentNotification;
        }

        public void setPaymentNotification(String paymentNotification) {
            this.paymentNotification = paymentNotification;
        }

        public String getPaymentNotificationDlq() {
            return paymentNotificationDlq;
        }

        public void setPaymentNotificationDlq(String paymentNotificationDlq) {
            this.paymentNotificationDlq = paymentNotificationDlq;
        }

        public String getReceiptEmail() {
            return receiptEmail;
        }

        public void setReceiptEmail(String receiptEmail) {
            this.receiptEmail = receiptEmail;
        }

        public String getReceiptEmailDlq() {
            return receiptEmailDlq;
        }

        public void setReceiptEmailDlq(String receiptEmailDlq) {
            this.receiptEmailDlq = receiptEmailDlq;
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

        public String getUserRegistered() {
            return userRegistered;
        }

        public void setUserRegistered(String userRegistered) {
            this.userRegistered = userRegistered;
        }

        public String getUserRegisteredDlq() {
            return userRegisteredDlq;
        }

        public void setUserRegisteredDlq(String userRegisteredDlq) {
            this.userRegisteredDlq = userRegisteredDlq;
        }
    }

    public static class RoutingKey {
        private String paymentNotificationDlq;
        private String receiptEmailDlq;
        private String orderCreatedDlq;
        private String userRegisteredDlq;

        public String getPaymentNotificationDlq() {
            return paymentNotificationDlq;
        }

        public void setPaymentNotificationDlq(String paymentNotificationDlq) {
            this.paymentNotificationDlq = paymentNotificationDlq;
        }

        public String getReceiptEmailDlq() {
            return receiptEmailDlq;
        }

        public void setReceiptEmailDlq(String receiptEmailDlq) {
            this.receiptEmailDlq = receiptEmailDlq;
        }

        public String getOrderCreatedDlq() {
            return orderCreatedDlq;
        }

        public void setOrderCreatedDlq(String orderCreatedDlq) {
            this.orderCreatedDlq = orderCreatedDlq;
        }

        public String getUserRegisteredDlq() {
            return userRegisteredDlq;
        }

        public void setUserRegisteredDlq(String userRegisteredDlq) {
            this.userRegisteredDlq = userRegisteredDlq;
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
