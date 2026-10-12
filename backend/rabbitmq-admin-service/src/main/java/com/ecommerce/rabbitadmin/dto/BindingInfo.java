package com.ecommerce.rabbitadmin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Resumen de un binding tal como lo entrega la Management HTTP API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BindingInfo(
        String source,
        String destination,
        @JsonProperty("destination_type") String destinationType,
        @JsonProperty("routing_key") String routingKey,
        @JsonProperty("properties_key") String propertiesKey,
        Map<String, Object> arguments
) {
}
