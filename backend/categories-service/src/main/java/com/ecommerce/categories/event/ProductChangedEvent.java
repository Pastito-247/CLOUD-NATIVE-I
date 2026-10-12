package com.ecommerce.categories.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Evento publicado por el dominio de productos cuando un producto cambia.
 * Es el contrato de mensajeria del exchange {@code catalog.exchange} con
 * routing keys {@code product.created}, {@code product.updated} y
 * {@code product.deleted}; categories-service lo consume para reaccionar
 * a cambios del catalogo.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductChangedEvent(
        Long productId,
        String name,
        String category,
        BigDecimal price,
        Integer stockQuantity,
        String changeType,
        LocalDateTime occurredAt
) {
}
