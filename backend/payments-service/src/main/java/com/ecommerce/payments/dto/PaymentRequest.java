package com.ecommerce.payments.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * Solicitud de pago recibida por la API. El pago no es real: se simula la
 * pasarela. Los items permiten descontar stock y armar la boleta.
 */
public record PaymentRequest(

        Long orderId,

        @NotBlank(message = "userEmail es obligatorio")
        String userEmail,

        @NotNull(message = "amount es obligatorio")
        @Positive(message = "amount debe ser mayor a 0")
        BigDecimal amount,

        @NotBlank(message = "method es obligatorio")
        String method,

        String shippingAddress,

        @NotEmpty(message = "items no puede estar vacio")
        List<@Valid OrderItemRequest> items
) {
}
