package com.ecommerce.rabbitadmin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Resumen de un exchange tal como lo entrega la Management HTTP API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExchangeInfo(
        String name,
        String type,
        Boolean durable,
        @JsonProperty("auto_delete") Boolean autoDelete,
        Map<String, Object> arguments
) {
}
