package com.ecommerce.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.HashMap;
import java.util.Map;

/**
 * DTO de entrada para crear un exchange.
 */
public class ExchangeDefinitionRequest {

    private static final String NAME_REGEX = "^[A-Za-z0-9._:\\-]{1,255}$";
    private static final String TYPE_REGEX = "^(direct|topic|fanout|headers)$";

    @NotBlank(message = "El nombre del exchange es obligatorio")
    @Pattern(regexp = NAME_REGEX,
            message = "El nombre del exchange solo admite letras, numeros, '.', '_', ':' y '-' (1-255 caracteres)")
    private String name;

    @NotBlank(message = "El tipo de exchange es obligatorio")
    @Pattern(regexp = TYPE_REGEX,
            message = "El tipo de exchange debe ser direct, topic, fanout o headers")
    private String type;

    private boolean durable = true;

    private boolean autoDelete = false;

    private Map<String, Object> arguments = new HashMap<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isDurable() {
        return durable;
    }

    public void setDurable(boolean durable) {
        this.durable = durable;
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
