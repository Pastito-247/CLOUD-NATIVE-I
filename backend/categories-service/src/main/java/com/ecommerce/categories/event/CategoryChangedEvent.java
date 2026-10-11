package com.ecommerce.categories.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * Evento publicado cuando una categoria cambia (creada, actualizada o eliminada).
 * Es el contrato de mensajeria del exchange {@code catalog.exchange} con
 * routing keys {@code category.created}, {@code category.updated} y
 * {@code category.deleted}. Lo consumen otros servicios del catalogo
 * (por ejemplo, products-service).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CategoryChangedEvent(
        Long categoryId,
        String name,
        String changeType,
        LocalDateTime occurredAt
) {
}
