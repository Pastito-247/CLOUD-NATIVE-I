package com.ecommerce.products.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Evento publicado cuando un producto cambia (creado, actualizado o eliminado).
 * Es el contrato de mensajeria del exchange {@code catalog.exchange} con
 * routing keys {@code product.created}, {@code product.updated} y
 * {@code product.deleted}. Lo consumen otros servicios del catalogo
 * (por ejemplo, categories-service).
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
