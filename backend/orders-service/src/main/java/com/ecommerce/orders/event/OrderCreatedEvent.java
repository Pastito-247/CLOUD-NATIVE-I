package com.ecommerce.orders.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Evento {@code order.created} publicado al crear un pedido.
 * Lo consumen otros servicios (p. ej. users-service genera el aviso
 * "pedido recibido"). Los nombres de campo forman parte del contrato JSON.
 */
public record OrderCreatedEvent(
        Long orderId,
        String userEmail,
        BigDecimal total,
        String shippingAddress,
        LocalDateTime createdAt
) {
}
