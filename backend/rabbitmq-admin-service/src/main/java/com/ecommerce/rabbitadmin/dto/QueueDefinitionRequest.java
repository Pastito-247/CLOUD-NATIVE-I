package com.ecommerce.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.HashMap;
import java.util.Map;

/**
 * DTO de entrada para crear una cola. Todos los parametros se validan antes
 * de tocar RabbitMQ.
 */
public class QueueDefinitionRequest {

    private static final String NAME_REGEX = "^[A-Za-z0-9._:\\-]{1,255}$";

    @NotBlank(message = "El nombre de la cola es obligatorio")
    @Pattern(regexp = NAME_REGEX,
            message = "El nombre de la cola solo admite letras, numeros, '.', '_', ':' y '-' (1-255 caracteres)")
    private String name;

    private boolean durable = true;

    private boolean exclusive = false;

    private boolean autoDelete = false;

    private Map<String, Object> arguments = new HashMap<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDurable() {
        return durable;
    }

    public void setDurable(boolean durable) {
        this.durable = durable;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    public void setExclusive(boolean exclusive) {
        this.exclusive = exclusive;
    }

    public boolean isAutoDelete() {
        return autoDelete;
    }

    public void setAutoDelete(boolean autoDelete) {
        this.autoDelete = autoDelete;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments == null ? new HashMap<>() : arguments;
    }
}
