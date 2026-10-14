package com.ecommerce.users.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Evento de solicitud de pago (contrato compartido con payments-service).
 * Se consume desde {@code payment.notification.queue} para avisar que el pago
 * esta siendo procesado.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentRequestedEvent(
        Long paymentId,
        Long orderId,
        String userEmail,
        BigDecimal amount,
        String method,
        String shippingAddress,
        List<OrderItemEvent> items
) {
}
