package com.ecommerce.rabbitadmin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Resumen de una cola tal como lo entrega la Management HTTP API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QueueInfo(
        String name,
        Boolean durable,
        @JsonProperty("auto_delete") Boolean autoDelete,
        String type,
        String state,
        Integer messages,
        Integer consumers,
        Map<String, Object> arguments
) {
}
