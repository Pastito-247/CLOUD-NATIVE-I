package com.ecommerce.rabbitadmin.exception;

/**
 * Parametros invalidos para una operacion de administracion (HTTP 400).
 */
public class InvalidRabbitResourceException extends RuntimeException {

    public InvalidRabbitResourceException(String message) {
        super(message);
    }
}
