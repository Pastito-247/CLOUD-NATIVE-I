package com.ecommerce.orders.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Evento publicado cuando se crea un pedido. Es el contrato de mensajeria
 * compartido por el exchange {@code orders.exchange} con routing key
 * {@code order.created}.
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
