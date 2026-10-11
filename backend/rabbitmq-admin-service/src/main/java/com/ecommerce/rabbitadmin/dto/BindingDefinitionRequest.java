package com.ecommerce.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.HashMap;
import java.util.Map;

/**
 * DTO de entrada para crear/eliminar un binding.
 */
public class BindingDefinitionRequest {

    private static final String NAME_REGEX = "^[A-Za-z0-9._:\\-]{1,255}$";

    @NotBlank(message = "El nombre del exchange es obligatorio")
    @Pattern(regexp = NAME_REGEX,
            message = "El nombre del exchange solo admite letras, numeros, '.', '_', ':' y '-'")
    private String exchange;

    @NotBlank(message = "El nombre de la cola es obligatorio")
    @Pattern(regexp = NAME_REGEX,
            message = "El nombre de la cola solo admite letras, numeros, '.', '_', ':' y '-'")
    private String queue;

    /** Routing key del binding (puede ser vacia para exchanges fanout/direct). */
    private String routingKey = "";

    private Map<String, Object> arguments = new HashMap<>();

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey == null ? "" : routingKey;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments == null ? new HashMap<>() : arguments;
    }
}
