package com.ecommerce.products.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion centralizada de mensajeria del catalogo.
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
        private String catalog;
        private String deadLetter;

        public String getCatalog() {
            return catalog;
        }

        public void setCatalog(String catalog) {
            this.catalog = catalog;
        }

        public String getDeadLetter() {
            return deadLetter;
        }

        public void setDeadLetter(String deadLetter) {
            this.deadLetter = deadLetter;
        }
    }

    public static class Queue {
        private String productSync;
        private String productSyncDlq;

        public String getProductSync() {
            return productSync;
        }

        public void setProductSync(String productSync) {
            this.productSync = productSync;
        }

        public String getProductSyncDlq() {
            return productSyncDlq;
        }

        public void setProductSyncDlq(String productSyncDlq) {
            this.productSyncDlq = productSyncDlq;
        }
    }

    public static class RoutingKey {
        private String productCreated;
        private String productUpdated;
        private String productDeleted;
        private String categoryCreated;
        private String categoryUpdated;
        private String categoryDeleted;
        private String productSyncDlq;

        public String getProductCreated() {
            return productCreated;
        }

        public void setProductCreated(String productCreated) {
            this.productCreated = productCreated;
        }

        public String getProductUpdated() {
            return productUpdated;
        }

        public void setProductUpdated(String productUpdated) {
            this.productUpdated = productUpdated;
        }

        public String getProductDeleted() {
            return productDeleted;
        }

        public void setProductDeleted(String productDeleted) {
            this.productDeleted = productDeleted;
        }

        public String getCategoryCreated() {
            return categoryCreated;
        }

        public void setCategoryCreated(String categoryCreated) {
            this.categoryCreated = categoryCreated;
        }

        public String getCategoryUpdated() {
            return categoryUpdated;
        }

        public void setCategoryUpdated(String categoryUpdated) {
            this.categoryUpdated = categoryUpdated;
        }

        public String getCategoryDeleted() {
            return categoryDeleted;
        }

        public void setCategoryDeleted(String categoryDeleted) {
            this.categoryDeleted = categoryDeleted;
        }

        public String getProductSyncDlq() {
            return productSyncDlq;
        }

        public void setProductSyncDlq(String productSyncDlq) {
            this.productSyncDlq = productSyncDlq;
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
