package com.ecommerce.payments.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion centralizada de mensajeria.
 *
 * Todos los nombres de exchanges, colas y routing keys se leen desde
 * application.yml (seccion {@code messaging}). Ningun nombre debe quedar
 * escrito directamente en la logica de negocio.
 */
@ConfigurationProperties(prefix = "messaging")
public class MessagingProperties {

    private final Exchange exchange = new Exchange();
    private final Queue queue = new Queue();
    private final RoutingKey routingKey = new RoutingKey();
    private final Processing processing = new Processing();
    private final Receipt receipt = new Receipt();
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

    public Processing getProcessing() {
        return processing;
    }

    public Receipt getReceipt() {
        return receipt;
    }

    public Retry getRetry() {
        return retry;
    }

    public static class Exchange {
        private String payment;
        private String paymentDeadLetter;
        private String receipt;
        private String receiptDeadLetter;

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
    }

    public static class Queue {
        private String paymentNotification;
        private String paymentScreen;
        private String paymentNotificationDlq;
        private String paymentScreenDlq;
        private String receiptEmail;
        private String orderReview;
        private String stockDecrement;
        private String receiptEmailDlq;
        private String orderReviewDlq;
        private String stockDecrementDlq;

        public String getPaymentNotification() {
            return paymentNotification;
        }

        public void setPaymentNotification(String paymentNotification) {
            this.paymentNotification = paymentNotification;
        }

        public String getPaymentScreen() {
            return paymentScreen;
        }

        public void setPaymentScreen(String paymentScreen) {
            this.paymentScreen = paymentScreen;
        }

        public String getPaymentNotificationDlq() {
            return paymentNotificationDlq;
        }

        public void setPaymentNotificationDlq(String paymentNotificationDlq) {
            this.paymentNotificationDlq = paymentNotificationDlq;
        }

        public String getPaymentScreenDlq() {
            return paymentScreenDlq;
        }

        public void setPaymentScreenDlq(String paymentScreenDlq) {
            this.paymentScreenDlq = paymentScreenDlq;
        }

        public String getReceiptEmail() {
            return receiptEmail;
        }

        public void setReceiptEmail(String receiptEmail) {
            this.receiptEmail = receiptEmail;
        }

        public String getOrderReview() {
            return orderReview;
        }

        public void setOrderReview(String orderReview) {
            this.orderReview = orderReview;
        }

        public String getStockDecrement() {
            return stockDecrement;
        }

        public void setStockDecrement(String stockDecrement) {
            this.stockDecrement = stockDecrement;
        }

        public String getReceiptEmailDlq() {
            return receiptEmailDlq;
        }

        public void setReceiptEmailDlq(String receiptEmailDlq) {
            this.receiptEmailDlq = receiptEmailDlq;
        }

        public String getOrderReviewDlq() {
            return orderReviewDlq;
        }

        public void setOrderReviewDlq(String orderReviewDlq) {
            this.orderReviewDlq = orderReviewDlq;
        }

        public String getStockDecrementDlq() {
            return stockDecrementDlq;
        }

        public void setStockDecrementDlq(String stockDecrementDlq) {
            this.stockDecrementDlq = stockDecrementDlq;
        }
    }

    public static class RoutingKey {
        private String paymentNotificationDlq;
        private String paymentScreenDlq;
        private String receiptEmailDlq;
        private String orderReviewDlq;
        private String stockDecrementDlq;

        public String getPaymentNotificationDlq() {
            return paymentNotificationDlq;
        }

        public void setPaymentNotificationDlq(String paymentNotificationDlq) {
            this.paymentNotificationDlq = paymentNotificationDlq;
        }

        public String getPaymentScreenDlq() {
            return paymentScreenDlq;
        }

        public void setPaymentScreenDlq(String paymentScreenDlq) {
            this.paymentScreenDlq = paymentScreenDlq;
        }

        public String getReceiptEmailDlq() {
            return receiptEmailDlq;
        }

        public void setReceiptEmailDlq(String receiptEmailDlq) {
            this.receiptEmailDlq = receiptEmailDlq;
        }

        public String getOrderReviewDlq() {
            return orderReviewDlq;
        }

        public void setOrderReviewDlq(String orderReviewDlq) {
            this.orderReviewDlq = orderReviewDlq;
        }

        public String getStockDecrementDlq() {
            return stockDecrementDlq;
        }

        public void setStockDecrementDlq(String stockDecrementDlq) {
            this.stockDecrementDlq = stockDecrementDlq;
        }
    }

    /**
     * Parametros de la simulacion del procesamiento de pago.
     */
    public static class Processing {
        private long delayMs = 3000;

        public long getDelayMs() {
            return delayMs;
        }

        public void setDelayMs(long delayMs) {
            this.delayMs = delayMs;
        }
    }

    /**
     * Parametros de la generacion de la boleta PDF.
     */
    public static class Receipt {
        private String storageDir = "receipts";

        public String getStorageDir() {
            return storageDir;
        }

        public void setStorageDir(String storageDir) {
            this.storageDir = storageDir;
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
