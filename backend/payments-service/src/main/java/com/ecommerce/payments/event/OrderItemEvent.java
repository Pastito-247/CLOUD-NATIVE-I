package com.ecommerce.payments.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Item comprado dentro de un pedido/pago. Contrato compartido con los
 * dominios de pedidos (revision) y productos (descuento de stock).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderItemEvent(
        Long productId,
        String productName,
        Integer quantity,
        BigDecimal unitPrice
) {
}
