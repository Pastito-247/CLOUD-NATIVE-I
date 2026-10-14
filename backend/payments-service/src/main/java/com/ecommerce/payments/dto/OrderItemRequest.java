package com.ecommerce.payments.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Item solicitado en un pago.
 */
public record OrderItemRequest(

        @NotNull(message = "productId es obligatorio")
        Long productId,

        String productName,

        @NotNull(message = "quantity es obligatorio")
        @Positive(message = "quantity debe ser mayor a 0")
        Integer quantity,

        BigDecimal unitPrice
) {
}
