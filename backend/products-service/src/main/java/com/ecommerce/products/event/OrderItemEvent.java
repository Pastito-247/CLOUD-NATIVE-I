package com.ecommerce.products.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Item comprado dentro de un pedido/pago (contrato compartido con el dominio
 * de pagos). Se usa para descontar el stock de cada producto.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderItemEvent(
        Long productId,
        String productName,
        Integer quantity,
        BigDecimal unitPrice
) {
}
