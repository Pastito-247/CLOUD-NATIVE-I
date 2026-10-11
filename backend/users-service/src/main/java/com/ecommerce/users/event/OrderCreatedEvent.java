package com.ecommerce.users.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Evento de pedido creado (contrato compartido con orders-service).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCreatedEvent(
        Long orderId,
        String userEmail,
        BigDecimal total,
        String status,
        String shippingAddress,
        LocalDateTime createdAt
) {
}
