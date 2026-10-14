package com.ecommerce.users.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Evento {@code order.created} publicado por orders-service al crear un
 * pedido. users-service lo consume para generar el aviso "pedido recibido".
 * Los nombres de campo deben coincidir con el productor (serializacion JSON).
 */
public record OrderCreatedEvent(
        Long orderId,
        String userEmail,
        BigDecimal total,
        String shippingAddress,
        LocalDateTime createdAt
) {
}
