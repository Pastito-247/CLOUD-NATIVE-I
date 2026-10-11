package com.ecommerce.products.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * Evento publicado por el dominio de categorias cuando una categoria cambia.
 * Es el contrato de mensajeria del exchange {@code catalog.exchange} con
 * routing keys {@code category.created}, {@code category.updated} y
 * {@code category.deleted}; products-service lo consume para reaccionar
 * a cambios del catalogo.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CategoryChangedEvent(
        Long categoryId,
        String name,
        String changeType,
        LocalDateTime occurredAt
) {
}
