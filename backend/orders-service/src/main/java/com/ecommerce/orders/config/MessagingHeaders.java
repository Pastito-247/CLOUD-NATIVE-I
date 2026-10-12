package com.ecommerce.orders.config;

/**
 * Cabeceras AMQP propias usadas para control de reintentos y trazabilidad.
 * Se centralizan aqui para no repetir cadenas literales en los consumidores.
 */
public final class MessagingHeaders {

    /** Numero de veces que un mensaje ha sido reintentado. */
    public static final String RETRY_COUNT = "x-retry-count";

    /** Momento en que el mensaje fue publicado originalmente. */
    public static final String ORIGINAL_TIMESTAMP = "x-original-timestamp";

    private MessagingHeaders() {
    }
}
