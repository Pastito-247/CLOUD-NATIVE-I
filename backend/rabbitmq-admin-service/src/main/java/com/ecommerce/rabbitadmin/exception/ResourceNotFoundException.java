package com.ecommerce.rabbitadmin.exception;

/**
 * Recurso de RabbitMQ inexistente (HTTP 404).
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
