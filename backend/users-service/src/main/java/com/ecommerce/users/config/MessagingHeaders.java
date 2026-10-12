package com.ecommerce.users.config;

/**
 * Cabeceras AMQP propias usadas para control de reintentos.
 */
public final class MessagingHeaders {

    /** Numero de veces que un mensaje ha sido reintentado. */
    public static final String RETRY_COUNT = "x-retry-count";

    private MessagingHeaders() {
    }
}
