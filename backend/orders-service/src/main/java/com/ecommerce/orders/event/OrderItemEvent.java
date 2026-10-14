package com.ecommerce.orders.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Item comprado dentro de un pedido/pago (contrato compartido con el dominio
 * de pagos). Se usa para armar el detalle del pedido en revision.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderItemEvent(
        Long productId,
        String productName,
        Integer quantity,
        BigDecimal unitPrice
) {
}
