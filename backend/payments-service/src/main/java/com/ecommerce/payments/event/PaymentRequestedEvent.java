package com.ecommerce.payments.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Evento publicado cuando el cliente solicita pagar un pedido.
 *
 * Es el contrato de mensajeria del exchange {@code payment.exchange} (fanout):
 * lo consumen la cola de notificacion (aviso "pago en proceso") y la cola de
 * pantalla/simulacion (que genera la boleta PDF).
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
