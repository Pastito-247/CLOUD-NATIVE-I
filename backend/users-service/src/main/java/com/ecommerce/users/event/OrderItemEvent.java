package com.ecommerce.users.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Item comprado dentro de un pedido/pago (contrato compartido con el dominio
 * de pagos). Solo se usa para armar el detalle de la notificacion.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderItemEvent(
        Long productId,
        String productName,
        Integer quantity,
        BigDecimal unitPrice
) {
}
